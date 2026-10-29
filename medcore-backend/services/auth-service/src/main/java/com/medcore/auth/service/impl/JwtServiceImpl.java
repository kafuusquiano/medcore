package com.medcore.auth.service.impl;

import com.medcore.auth.config.AuthProperties;
import com.medcore.auth.config.JwtProperties;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.exception.AuthException;
import com.medcore.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Emision y validacion de JWT, con revocacion respaldada en Redis.
 *
 * <p>La lista negra guarda el identificador del token, no el token entero, y
 * con un TTL igual a su vigencia restante: asi Redis libera la clave sola
 * cuando el token ya no serviria de todas formas, y no crece sin limite.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);

    private static final String CLAIM_ROL = "rol";
    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_ACCESO = "access";
    private static final String TIPO_REFRESH = "refresh";

    private final SecretKey clave;
    private final JwtProperties jwtProperties;
    private final String blacklistPrefix;
    private final StringRedisTemplate redis;

    public JwtServiceImpl(JwtProperties jwtProperties,
                          AuthProperties authProperties,
                          StringRedisTemplate redis) {
        byte[] bytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        // HS512 exige 512 bits. Fallar al arrancar es preferible a descubrirlo
        // en el primer login de la demostracion.
        if (bytes.length < 64) {
            throw new IllegalStateException(
                    "jwt.secret debe tener al menos 64 caracteres; se recibieron " + bytes.length
                            + ". Genera uno con: openssl rand -base64 64");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.jwtProperties = jwtProperties;
        this.blacklistPrefix = authProperties.blacklistPrefix();
        this.redis = redis;
    }

    @Override
    public String generarAccessToken(User usuario) {
        return construir(usuario, TIPO_ACCESO, jwtProperties.expirationMs());
    }

    @Override
    public String generarRefreshToken(User usuario) {
        return construir(usuario, TIPO_REFRESH, jwtProperties.refreshExpirationMs());
    }

    private String construir(User usuario, String tipo, long vigenciaMs) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + vigenciaMs);

        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                // El gateway lee este claim y lo propaga en X-User-Role (MC4).
                .claim(CLAIM_ROL, usuario.getRole().getNombre().name())
                .claim(CLAIM_TIPO, tipo)
                .issuer("medcore-auth")
                // jti identifica el token en la lista negra sin guardarlo entero.
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(ahora)
                .expiration(expira)
                .signWith(clave)
                .compact();
    }

    @Override
    public Long validarRefreshToken(String token) {
        Claims claims = leerClaims(token);

        if (!TIPO_REFRESH.equals(claims.get(CLAIM_TIPO, String.class))) {
            throw AuthException.noEsRefreshToken();
        }
        if (estaRevocado(token)) {
            throw AuthException.tokenRevocado();
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw AuthException.tokenInvalido();
        }
    }

    @Override
    public void revocar(String token) {
        Claims claims;
        try {
            claims = leerClaims(token);
        } catch (AuthException e) {
            // Revocar un token ya expirado o invalido no es un error: el
            // objetivo del logout es que deje de servir, y ya no sirve.
            log.debug("Se intento revocar un token no valido; se ignora");
            return;
        }

        long restanteMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (restanteMs <= 0) {
            return;
        }

        String jti = claims.getId();
        if (jti == null) {
            log.warn("Token sin jti; no se puede revocar de forma individual");
            return;
        }
        redis.opsForValue().set(blacklistPrefix + jti, "1", Duration.ofMillis(restanteMs));
    }

    @Override
    public boolean estaRevocado(String token) {
        try {
            String jti = leerClaims(token).getId();
            return jti != null && Boolean.TRUE.equals(redis.hasKey(blacklistPrefix + jti));
        } catch (AuthException e) {
            // Un token ilegible se trata como no revocado; quien lo valide
            // lo rechazara igual por invalido.
            return false;
        }
    }

    @Override
    public long expiracionSegundos() {
        return jwtProperties.expiracionSegundos();
    }

    private Claims leerClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw AuthException.tokenExpirado();
        } catch (JwtException | IllegalArgumentException e) {
            // No se registra el token: seria un credencial en los logs.
            log.debug("Token rechazado: {}", e.getClass().getSimpleName());
            throw AuthException.tokenInvalido();
        }
    }
}
