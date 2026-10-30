package com.medcore.notification.exception;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.exception.BaseGlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler de notification-service. Hereda lo comun (G3) y anade los casos de
 * persistencia y de estado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    /**
     * Salto el indice unico de idempotencia (event_id, tipo, canal).
     *
     * <p>En los consumidores ya se atrapa y se trata como lo que es, un
     * reenvio inofensivo. Si llega hasta aqui es por una peticion HTTP, y
     * entonces si es un conflicto que el cliente debe conocer.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> manejarIntegridad(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "DATOS_DUPLICADOS",
                "Ya existe un aviso para ese evento, tipo y canal.");
    }

    /**
     * El aviso lo modifico otra operacion a la vez.
     *
     * <p>Ocurre cuando la pasada de la cola y una peticion de "marcar leido"
     * coinciden sobre el mismo aviso. Reintentar casi siempre lo resuelve.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> manejarConcurrencia(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "El aviso fue modificado por otra operacion. Vuelve a cargarlo e intentalo de nuevo.");
    }

    /** Transicion de estado no permitida en el aviso. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> manejarEstadoInvalido(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "OPERACION_NO_PERMITIDA",
                ex.getMessage());
    }
}
