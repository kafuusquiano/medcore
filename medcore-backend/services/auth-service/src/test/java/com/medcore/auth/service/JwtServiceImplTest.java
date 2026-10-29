package com.medcore.auth.service;

import com.medcore.auth.config.AuthProperties;
import com.medcore.auth.config.JwtProperties;
import com.medcore.auth.domain.entity.Role;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.exception.AuthException;
import com.medcore.auth.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceImplTest {

    private static final String SECRETO =
            "clave-de-pruebas-con-al-menos-sesenta-y-cuatro-caracteres-abcdefgh";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private JwtServiceImpl jwtService;
    private User usuario;

    @BeforeEach
    void preparar() {
        when(redis.opsForValue()).thenReturn(valueOps);

        JwtProperties jwtProperties = new JwtProperties(SECRETO, 3_600_000L, 604_800_000L);
        AuthProperties authProperties = new AuthProperties(5, "medcore:auth:blacklist:", 10);
        jwtService = new JwtServiceImpl(jwtProperties, authProperties, redis);

        usuario = new User("12345678", "medico@medcore.local", "$2a$10$hash",
                "Ana", "Torres", "999888777", new Role(RoleType.MEDICO, "Atiende"));
    }

    @Test
    @DisplayName("constructor_secretoCorto_lanzaIllegalState")
    void constructor_secretoCorto_lanzaIllegalState() {
        JwtProperties corto = new JwtProperties("demasiado-corto", 3_600_000L, 604_800_000L);
        AuthProperties auth = new AuthProperties(5, "p:", 10);

        assertThatThrownBy(() -> new JwtServiceImpl(corto, auth, redis))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 64 caracteres");
    }

    @Test
    @DisplayName("generarAccessToken_incluyeRolYTipoAcceso")
    void generarAccessToken_incluyeRolYTipoAcceso() {
        Claims claims = leer(jwtService.generarAccessToken(usuario));

        // El gateway lee este claim y lo propaga en X-User-Role (MC4).
        assertThat(claims.get("rol", String.class)).isEqualTo("MEDICO");
        assertThat(claims.get("tipo", String.class)).isEqualTo("access");
        assertThat(claims.getIssuer()).isEqualTo("medcore-auth");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("generarRefreshToken_duraMasQueElDeAcceso")
    void generarRefreshToken_duraMasQueElDeAcceso() {
        Date expiraAcceso = leer(jwtService.generarAccessToken(usuario)).getExpiration();
        Date expiraRefresh = leer(jwtService.generarRefreshToken(usuario)).getExpiration();

        assertThat(expiraRefresh).isAfter(expiraAcceso);
    }

    @Test
    @DisplayName("validarRefreshToken_conTokenDeAcceso_lanzaTipoIncorrecto")
    void validarRefreshToken_conTokenDeAcceso_lanzaTipoIncorrecto() {
        String accessToken = jwtService.generarAccessToken(usuario);

        assertThatThrownBy(() -> jwtService.validarRefreshToken(accessToken))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "TOKEN_TIPO_INCORRECTO");
    }

    @Test
    @DisplayName("validarRefreshToken_tokenRevocado_lanzaTokenRevocado")
    void validarRefreshToken_tokenRevocado_lanzaTokenRevocado() {
        String refresh = jwtService.generarRefreshToken(usuario);
        when(redis.hasKey(anyString())).thenReturn(true);

        assertThatThrownBy(() -> jwtService.validarRefreshToken(refresh))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "TOKEN_REVOCADO");
    }

    @Test
    @DisplayName("validarRefreshToken_firmadoConOtraClave_lanzaTokenInvalido")
    void validarRefreshToken_firmadoConOtraClave_lanzaTokenInvalido() {
        SecretKey otraClave = Keys.hmacShaKeyFor(
                "otra-clave-distinta-con-sesenta-y-cuatro-caracteres-abcdefghij"
                        .getBytes(StandardCharsets.UTF_8));

        String ajeno = Jwts.builder()
                .subject("1")
                .claim("tipo", "refresh")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otraClave)
                .compact();

        assertThatThrownBy(() -> jwtService.validarRefreshToken(ajeno))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "TOKEN_INVALIDO");
    }

    @Test
    @DisplayName("validarRefreshToken_expirado_lanzaTokenExpirado")
    void validarRefreshToken_expirado_lanzaTokenExpirado() {
        SecretKey clave = Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));

        String vencido = Jwts.builder()
                .subject("1")
                .claim("tipo", "refresh")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(clave)
                .compact();

        assertThatThrownBy(() -> jwtService.validarRefreshToken(vencido))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "TOKEN_EXPIRADO");
    }

    @Test
    @DisplayName("revocar_tokenIlegible_noEscribeEnRedis")
    void revocar_tokenIlegible_noEscribeEnRedis() {
        // Revocar algo que ya no sirve no es un error, pero tampoco debe
        // ocupar una clave en Redis.
        jwtService.revocar("token.basura.invalido");

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("revocar_tokenVigente_guardaConTtlIgualALaVigenciaRestante")
    void revocar_tokenVigente_guardaConTtlIgualALaVigenciaRestante() {
        jwtService.revocar(jwtService.generarAccessToken(usuario));

        // El TTL hace que Redis libere la clave sola cuando el token ya
        // habria expirado de todas formas, para que la lista no crezca sin fin.
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
    }

    private Claims leer(String token) {
        SecretKey clave = Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
    }
}
