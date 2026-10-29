package com.medcore.auth.service;

import com.medcore.auth.config.AuthProperties;
import com.medcore.auth.domain.dto.request.LoginRequest;
import com.medcore.auth.domain.dto.response.LoginResponse;
import com.medcore.auth.domain.dto.response.UserResponse;
import com.medcore.auth.domain.entity.Role;
import com.medcore.auth.domain.entity.SessionLog;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.exception.AuthException;
import com.medcore.auth.mapper.UserMapper;
import com.medcore.auth.repository.UserRepository;
import com.medcore.auth.service.impl.AuthAuditService;
import com.medcore.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final int MAX_INTENTOS = 5;
    private static final String IP = "190.12.0.1";
    private static final String AGENTE = "JUnit";
    private static final String REQUEST_ID = "req-test-1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthAuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    private User usuarioActivo;

    @BeforeEach
    void preparar() {
        AuthProperties properties = new AuthProperties(MAX_INTENTOS, "medcore:auth:blacklist:", 10);
        authService = new AuthServiceImpl(userRepository, auditService, passwordEncoder,
                jwtService, userMapper, properties);

        Role rolMedico = new Role(RoleType.MEDICO, "Atiende consultas");
        usuarioActivo = new User("12345678", "medico@medcore.local", "$2a$10$hash",
                "Ana", "Torres", "999888777", rolMedico);
        // Sin id, los matchers anyLong() de Mockito no coinciden: anyLong()
        // no acepta null, y el stub de registrarIntentoFallido no se aplicaria.
        ReflectionTestUtils.setField(usuarioActivo, "id", 1L);

        when(jwtService.generarAccessToken(any())).thenReturn("access-token");
        when(jwtService.generarRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.expiracionSegundos()).thenReturn(3600L);
        when(userMapper.aRespuesta(any())).thenReturn(
                new UserResponse(1L, "12345678", "medico@medcore.local", "Ana", "Torres",
                        "999888777", RoleType.MEDICO, usuarioActivo.getEstado(), null, null, null));
    }

    @Test
    @DisplayName("login_credencialesCorrectas_devuelveTokens")
    void login_credencialesCorrectas_devuelveTokens() {
        when(userRepository.buscarPorDniOCorreo("12345678")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRepository.save(any())).thenReturn(usuarioActivo);

        LoginResponse respuesta = authService.login(
                new LoginRequest("12345678", "Password1!"), IP, AGENTE, REQUEST_ID);

        assertThat(respuesta.accessToken()).isEqualTo("access-token");
        assertThat(respuesta.refreshToken()).isEqualTo("refresh-token");
        assertThat(respuesta.tipoToken()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEnSegundos()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("login_usuarioNoExiste_lanzaCredencialesInvalidas")
    void login_usuarioNoExiste_lanzaCredencialesInvalidas() {
        when(userRepository.buscarPorDniOCorreo(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("99999999", "Password1!"), IP, AGENTE, REQUEST_ID))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_INVALIDAS");
    }

    @Test
    @DisplayName("login_usuarioNoExiste_igualCifraParaNoRevelarSiExiste")
    void login_usuarioNoExiste_igualCifraParaNoRevelarSiExiste() {
        when(userRepository.buscarPorDniOCorreo(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("99999999", "Password1!"), IP, AGENTE, REQUEST_ID))
                .isInstanceOf(AuthException.class);

        // Se compara contra un hash ficticio para que el tiempo de respuesta
        // no delate si el DNI esta registrado.
        verify(passwordEncoder).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("login_passwordIncorrecta_registraIntentoEnTransaccionPropia")
    void login_passwordIncorrecta_registraIntentoEnTransaccionPropia() {
        when(userRepository.buscarPorDniOCorreo(anyString())).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(auditService.registrarIntentoFallido(anyLong(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("12345678", "incorrecta"), IP, AGENTE, REQUEST_ID))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_INVALIDAS");

        // Debe delegarse en AuthAuditService: guardarlo en esta transaccion
        // haria que el throw revirtiera el contador y la cuenta no se
        // bloquearia nunca.
        verify(auditService).registrarIntentoFallido(usuarioActivo.getId(), MAX_INTENTOS);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login_quintoIntentoFallido_lanzaCuentaBloqueada")
    void login_quintoIntentoFallido_lanzaCuentaBloqueada() {
        when(userRepository.buscarPorDniOCorreo(anyString())).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(auditService.registrarIntentoFallido(anyLong(), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("12345678", "incorrecta"), IP, AGENTE, REQUEST_ID))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "CUENTA_BLOQUEADA");
    }

    @Test
    @DisplayName("login_cuentaDesactivada_lanzaCuentaInactiva")
    void login_cuentaDesactivada_lanzaCuentaInactiva() {
        usuarioActivo.desactivar();
        when(userRepository.buscarPorDniOCorreo(anyString())).thenReturn(Optional.of(usuarioActivo));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("12345678", "Password1!"), IP, AGENTE, REQUEST_ID))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("codigo", "CUENTA_INACTIVA");

        // No se llega a comparar la contrasena de una cuenta desactivada.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("login_exitoso_registraAccesoEnBitacora")
    void login_exitoso_registraAccesoEnBitacora() {
        when(userRepository.buscarPorDniOCorreo(anyString())).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRepository.save(any())).thenReturn(usuarioActivo);

        authService.login(new LoginRequest("12345678", "Password1!"), IP, AGENTE, REQUEST_ID);

        ArgumentCaptor<SessionLog> captor = ArgumentCaptor.forClass(SessionLog.class);
        verify(auditService).registrar(captor.capture());

        SessionLog registro = captor.getValue();
        assertThat(registro.isExitoso()).isTrue();
        assertThat(registro.getIpOrigen()).isEqualTo(IP);
        assertThat(registro.getRequestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    @DisplayName("logout_tokenNulo_noFalla")
    void logout_tokenNulo_noFalla() {
        authService.logout(null, null);
        verify(jwtService, never()).revocar(anyString());
    }

    @Test
    @DisplayName("logout_conAmbosTokens_revocaLosDos")
    void logout_conAmbosTokens_revocaLosDos() {
        authService.logout("access", "refresh");

        verify(jwtService).revocar("access");
        verify(jwtService).revocar("refresh");
    }
}
