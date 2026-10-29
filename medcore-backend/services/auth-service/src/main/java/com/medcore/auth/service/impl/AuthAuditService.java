package com.medcore.auth.service.impl;

import com.medcore.auth.domain.entity.SessionLog;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.repository.SessionLogRepository;
import com.medcore.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrituras que deben sobrevivir al fallo del login.
 *
 * <p>Motivo: {@code AuthServiceImpl.login} es transaccional y termina lanzando
 * una excepcion cuando las credenciales no son validas. Esa excepcion revierte
 * la transaccion, y con ella el contador de intentos fallidos y la fila de
 * auditoria que se acababan de guardar. El resultado observado era que la
 * cuenta nunca llegaba a bloquearse y que los intentos fallidos no quedaban
 * registrados en session_log.
 *
 * <p>Por eso estos metodos corren en una transaccion propia
 * ({@code REQUIRES_NEW}), que confirma aunque la externa revierta. Al ser un
 * bean distinto, la llamada pasa por el proxy de Spring y la propagacion se
 * aplica de verdad; si estuvieran en la misma clase, la llamada interna la
 * ignoraria.
 */
@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final UserRepository userRepository;
    private final SessionLogRepository sessionLogRepository;

    public AuthAuditService(UserRepository userRepository,
                            SessionLogRepository sessionLogRepository) {
        this.userRepository = userRepository;
        this.sessionLogRepository = sessionLogRepository;
    }

    /**
     * Registra un acceso en la bitacora.
     *
     * <p>Un fallo al auditar no debe impedir que el usuario reciba su
     * respuesta, asi que se traga la excepcion y se deja constancia en el log.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(SessionLog registro) {
        try {
            sessionLogRepository.save(registro);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el acceso en session_log: {}", e.getMessage());
        }
    }

    /**
     * Suma un intento fallido al usuario y lo bloquea si alcanza el maximo.
     *
     * @return true si este intento provoco el bloqueo
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarIntentoFallido(Long userId, int maximo) {
        User usuario = userRepository.findById(userId).orElse(null);
        if (usuario == null) {
            return false;
        }
        boolean bloqueada = usuario.registrarIntentoFallido(maximo);
        userRepository.save(usuario);
        return bloqueada;
    }
}
