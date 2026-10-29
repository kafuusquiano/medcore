package com.medcore.auth.service.impl;

import com.medcore.auth.config.AuthProperties;
import com.medcore.auth.domain.dto.request.LoginRequest;
import com.medcore.auth.domain.dto.request.RefreshTokenRequest;
import com.medcore.auth.domain.dto.response.LoginResponse;
import com.medcore.auth.domain.entity.SessionLog;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.exception.AuthException;
import com.medcore.auth.mapper.UserMapper;
import com.medcore.auth.repository.UserRepository;
import com.medcore.auth.service.AuthService;
import com.medcore.auth.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Inicio de sesion, renovacion y cierre.
 *
 * <p>Toda salida fallida del login responde lo mismo, {@code CREDENCIALES_INVALIDAS},
 * tanto si el usuario no existe como si la contrasena es incorrecta. Devolver
 * mensajes distintos permitiria enumerar que DNI estan registrados en la clinica.
 * Las unicas excepciones son la cuenta bloqueada y la inactiva, donde el usuario
 * necesita saber que debe contactar al administrador.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final AuthAuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuthProperties authProperties;

    public AuthServiceImpl(UserRepository userRepository,
                           AuthAuditService auditService,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           UserMapper userMapper,
                           AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipOrigen,
                               String dispositivo, String requestId) {

        Optional<User> encontrado = userRepository.buscarPorDniOCorreo(request.identificador());

        if (encontrado.isEmpty()) {
            // Se cifra igualmente contra un hash ficticio para que el tiempo de
            // respuesta no delate si el usuario existe (ataque por temporizacion).
            passwordEncoder.matches(request.password(), HASH_FICTICIO);
            auditService.registrar(SessionLog.fallo(null, request.identificador(), "USUARIO_NO_EXISTE",
                    ipOrigen, dispositivo, requestId));
            throw AuthException.credencialesInvalidas();
        }

        User usuario = encontrado.get();

        if (!usuario.puedeIniciarSesion()) {
            String motivo = usuario.getEstado().name();
            auditService.registrar(SessionLog.fallo(usuario.getId(), request.identificador(), motivo,
                    ipOrigen, dispositivo, requestId));
            throw switch (usuario.getEstado()) {
                case BLOCKED -> AuthException.cuentaBloqueada(authProperties.maxIntentosFallidos());
                case INACTIVE -> AuthException.cuentaInactiva();
                default -> AuthException.credencialesInvalidas();
            };
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            // En transaccion propia: si se guardara aqui, el throw de abajo
            // revertiria el contador y la cuenta no se bloquearia nunca.
            boolean bloqueada = auditService.registrarIntentoFallido(
                    usuario.getId(), authProperties.maxIntentosFallidos());
            auditService.registrar(SessionLog.fallo(usuario.getId(), request.identificador(),
                    bloqueada ? "BLOQUEO_POR_INTENTOS" : "PASSWORD_INCORRECTA",
                    ipOrigen, dispositivo, requestId));

            if (bloqueada) {
                log.warn("Cuenta {} bloqueada tras {} intentos fallidos",
                        usuario.getId(), authProperties.maxIntentosFallidos());
                throw AuthException.cuentaBloqueada(authProperties.maxIntentosFallidos());
            }
            throw AuthException.credencialesInvalidas();
        }

        usuario.registrarAccesoExitoso();
        userRepository.save(usuario);
        auditService.registrar(SessionLog.exito(usuario.getId(), request.identificador(),
                ipOrigen, dispositivo, requestId));

        log.info("Inicio de sesion correcto del usuario {} con rol {}",
                usuario.getId(), usuario.getRole().getNombre());

        return LoginResponse.de(
                jwtService.generarAccessToken(usuario),
                jwtService.generarRefreshToken(usuario),
                jwtService.expiracionSegundos(),
                userMapper.aRespuesta(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse refrescar(RefreshTokenRequest request) {
        Long userId = jwtService.validarRefreshToken(request.refreshToken());

        User usuario = userRepository.findById(userId)
                .orElseThrow(AuthException::credencialesInvalidas);

        // La cuenta pudo desactivarse despues de emitir el refresh token.
        if (!usuario.puedeIniciarSesion()) {
            throw usuario.getEstado() == com.medcore.auth.domain.enums.UserStatus.BLOCKED
                    ? AuthException.cuentaBloqueada(authProperties.maxIntentosFallidos())
                    : AuthException.cuentaInactiva();
        }

        return LoginResponse.de(
                jwtService.generarAccessToken(usuario),
                // Se emite un refresh nuevo y el anterior deja de usarse: asi un
                // token robado tiene una ventana de uso acotada (rotacion).
                jwtService.generarRefreshToken(usuario),
                jwtService.expiracionSegundos(),
                userMapper.aRespuesta(usuario));
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            jwtService.revocar(accessToken);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            jwtService.revocar(refreshToken);
        }
    }

    /**
     * Hash BCrypt de un valor arbitrario. Solo se usa para consumir el mismo
     * tiempo de CPU cuando el usuario no existe.
     */
    private static final String HASH_FICTICIO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
}
