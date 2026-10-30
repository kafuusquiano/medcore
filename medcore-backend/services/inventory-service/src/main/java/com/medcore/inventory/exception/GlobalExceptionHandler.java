package com.medcore.inventory.exception;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.exception.BaseGlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler de inventory-service. Hereda lo comun (G3) y anade los casos de
 * persistencia y los de cantidad invalida.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    /**
     * Pudo saltar ux_stock_reactivo_lote, el codigo unico del reactivo o una
     * de las restricciones CHECK de movement_logs. El mensaje es generico
     * porque desde aqui no se sabe cual: los casos previsibles se comprueban
     * antes en el servicio y devuelven un codigo especifico.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> manejarIntegridad(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "DATOS_DUPLICADOS",
                "Ya existe un registro con esos datos, o la operacion deja el inventario "
                        + "en un estado invalido.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> manejarConcurrencia(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "El registro fue modificado por otra operacion. Vuelve a cargarlo e intentalo de nuevo.");
    }

    /**
     * El bloqueo pesimista de buscarParaMover agoto su espera de 5 segundos.
     * Significa que otra transaccion tiene el lote tomado mas tiempo del
     * esperado, no que la peticion sea incorrecta: reintentar suele resolverlo.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> manejarBloqueo(PessimisticLockingFailureException ex,
                                                            HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "LOTE_OCUPADO",
                "Otra operacion esta moviendo ese lote en este momento. Intentalo de nuevo.");
    }

    /**
     * Un movimiento que dejaria el lote en negativo. Lo rechaza la entidad,
     * que es donde vive la regla, y se traduce aqui a 409.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> manejarCantidadInvalida(IllegalArgumentException ex,
                                                                     HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "STOCK_INSUFICIENTE",
                ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> manejarEstadoInvalido(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "OPERACION_NO_PERMITIDA",
                ex.getMessage());
    }
}
