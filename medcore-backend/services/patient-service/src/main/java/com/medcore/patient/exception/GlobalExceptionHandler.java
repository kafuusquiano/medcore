package com.medcore.patient.exception;

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
 * Handler de patient-service. Hereda lo comun de
 * {@link BaseGlobalExceptionHandler} (G3) y anade los casos de persistencia.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    /**
     * Dos altas con el mismo DNI que llegan a la vez: la comprobacion previa
     * pasa en ambas y solo la base puede decidir cual gana.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> manejarIntegridad(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "DATOS_DUPLICADOS",
                "Ya existe un registro con esos datos.");
    }

    /** Dos ediciones simultaneas de la misma ficha (@Version). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> manejarConcurrencia(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "La ficha fue modificada por otra operacion. Vuelve a cargarla e intentalo de nuevo.");
    }

    /** Reactivar la ficha de un paciente fallecido. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> manejarEstadoInvalido(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "OPERACION_NO_PERMITIDA",
                ex.getMessage());
    }
}
