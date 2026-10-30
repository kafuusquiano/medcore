package com.medcore.laboratory.exception;

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
 * Handler de laboratory-service. Hereda lo comun (G3) y anade los casos de
 * persistencia y de estado.
 *
 * <p>No maneja {@link Hl7ParseException}: la hereda de MedCoreException y el
 * handler base ya la traduce con su codigo y su 422. Lo que ademas necesita
 * el equipo es un ACK de error, y eso lo resuelve Hl7Controller en la propia
 * respuesta, porque solo alli se sabe el control id del mensaje al que hay
 * que responder.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    /**
     * Pudo saltar ux_sample_orden_viva, ux_sample codigo o
     * ux_result_muestra_prueba_vigente. El mensaje es generico porque desde
     * aqui no se sabe cual: los casos previsibles se comprueban antes en el
     * servicio y devuelven un codigo especifico.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> manejarIntegridad(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "DATOS_DUPLICADOS",
                "Ya existe un registro con esos datos.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> manejarConcurrencia(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "La muestra fue modificada por otra operacion. Vuelve a cargarla e intentalo de nuevo.");
    }

    /** Transicion de estado no permitida en la muestra. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> manejarEstadoInvalido(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "OPERACION_NO_PERMITIDA",
                ex.getMessage());
    }
}
