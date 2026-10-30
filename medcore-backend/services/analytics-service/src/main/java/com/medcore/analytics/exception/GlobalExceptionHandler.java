package com.medcore.analytics.exception;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.exception.BaseGlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler de analytics-service. Hereda lo comun (G3) y anade los casos de
 * MongoDB.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    /**
     * Salto un indice unico: el de idResultado en los registros, o el parcial
     * de una sola alerta abierta por zona y prueba.
     *
     * <p>En el consumidor y en el detector ya se atrapa y se trata como lo que
     * es, un reintento o una carrera inofensiva. Si llega hasta aqui es por una
     * peticion HTTP, y entonces si es un conflicto que el cliente debe conocer.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> manejarDuplicado(DuplicateKeyException ex,
                                                              HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "DATOS_DUPLICADOS",
                "Ya existe un registro con esos datos.");
    }

    /**
     * Fallo de una agregacion o de la conexion a Mongo.
     *
     * <p>Se responde 503 y no 500: el panel puede reintentar, y casi siempre
     * es la base momentaneamente inaccesible y no un error de la peticion.
     */
    @ExceptionHandler(UncategorizedMongoDbException.class)
    public ResponseEntity<ApiResponse<Void>> manejarMongo(UncategorizedMongoDbException ex,
                                                          HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.SERVICE_UNAVAILABLE, "ANALITICA_NO_DISPONIBLE",
                "La base de analitica no esta disponible en este momento. Intentalo de nuevo.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> manejarEstadoInvalido(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.CONFLICT, "OPERACION_NO_PERMITIDA",
                ex.getMessage());
    }
}
