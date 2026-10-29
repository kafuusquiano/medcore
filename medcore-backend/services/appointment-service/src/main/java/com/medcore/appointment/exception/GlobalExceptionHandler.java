package com.medcore.appointment.exception;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.exception.BaseGlobalExceptionHandler;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler de appointment-service. Hereda lo comun (G3) y anade los casos de
 * concurrencia, que aqui son frecuentes porque los slots son un recurso en
 * disputa entre varias reservas simultaneas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    /**
     * El indice unico ux_appointment_slot_activa rechazo una segunda cita
     * sobre el mismo slot. Es la red de seguridad por debajo del bloqueo
     * pesimista, para el caso de dos instancias del servicio compitiendo.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> manejarIntegridad(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "HORARIO_OCUPADO",
                "Ese horario acaba de ser reservado. Elige otro.");
    }

    /** No se pudo obtener el bloqueo del slot dentro del plazo de 5 segundos. */
    @ExceptionHandler({PessimisticLockException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiResponse<Void>> manejarBloqueo(Exception ex,
                                                            HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "HORARIO_EN_DISPUTA",
                "Otra reserva esta en curso sobre ese horario. Intentalo de nuevo.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> manejarConcurrencia(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "El registro fue modificado por otra operacion. Vuelve a cargarlo e intentalo de nuevo.");
    }

    /** Transicion de estado no permitida, declarada en AppointmentStatus. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> manejarEstadoInvalido(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "TRANSICION_NO_PERMITIDA",
                ex.getMessage());
    }
}
