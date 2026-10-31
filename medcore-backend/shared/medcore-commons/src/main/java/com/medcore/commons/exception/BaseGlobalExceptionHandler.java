package com.medcore.commons.exception;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.ErrorResponse;
import com.medcore.commons.security.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Handler base que todos los microservicios extienden (regla G3).
 *
 * <p>Centraliza la traduccion de excepciones a {@link ApiResponse}. Cada
 * servicio hereda de aqui y anade solo los casos propios de su dominio.
 *
 * <p>Nunca se devuelve el stack trace al cliente: el detalle tecnico queda en
 * el log asociado al {@code X-Request-Id} que propago el gateway (MC4), de modo
 * que un error reportado por el usuario se puede localizar en los logs.
 */
public abstract class BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseGlobalExceptionHandler.class);

    /** Excepciones de negocio: el codigo HTTP lo decide la propia excepcion. */
    @ExceptionHandler(MedCoreException.class)
    public ResponseEntity<ApiResponse<Void>> manejarMedCore(MedCoreException ex,
                                                            HttpServletRequest request) {
        String requestId = requestId(request);
        log.debug("[{}] {} en {}: {}", requestId, ex.getCodigo(), request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                ex.getCodigo(), ex.getEstado().value(), request.getRequestURI(), requestId,
                null, ex.getDetalles().isEmpty() ? null : ex.getDetalles());

        return ResponseEntity.status(ex.getEstado()).body(ApiResponse.fail(ex.getMessage(), error));
    }

    /** Validaciones de @Valid: se devuelve el detalle campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> manejarValidacion(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        List<ErrorResponse.FieldError> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        String requestId = requestId(request);
        log.debug("[{}] Validacion fallida en {}: {} campos", requestId, request.getRequestURI(), campos.size());

        ErrorResponse error = ErrorResponse.ofValidacion(
                "VALIDACION_FALLIDA", HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), requestId, campos);

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("Los datos enviados no son validos.", error));
    }

    /** Cuerpo JSON ilegible o con tipos incompatibles. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> manejarJsonInvalido(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "El cuerpo de la peticion no tiene un formato valido.");
    }

    /** Parametro obligatorio ausente en la query string. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> manejarParametroAusente(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.BAD_REQUEST, "PARAMETRO_AUSENTE",
                "Falta el parametro obligatorio: " + ex.getParameterName() + ".");
    }

    /** Parametro con tipo incorrecto, por ejemplo un texto donde se espera un numero. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> manejarTipoInvalido(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parametro " + ex.getName() + " tiene un valor invalido.");
    }

    /** Rol insuficiente para la operacion (@PreAuthorize). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> manejarAccesoDenegado(AccessDeniedException ex,
                                                                   HttpServletRequest request) {
        return respuestaSimple(request, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "No tienes permisos para realizar esta operacion.");
    }

    /**
     * Una URL que no existe.
     *
     * <p>Se manejan las DOS excepciones a proposito. Spring lanza
     * NoHandlerFoundException solo si se configura
     * {@code throw-exception-if-no-handler-found}, y en Spring Boot 4 lo
     * normal es que el manejador de recursos estaticos lance
     * NoResourceFoundException en su lugar.
     *
     * <p>Sin la segunda, una URL mal escrita caia en el manejador generico de
     * abajo y el cliente recibia un 500 con "ocurrio un error inesperado". Dos
     * problemas: el frontend no puede distinguir "no existe" de "se rompio", y
     * cada peticion a una ruta equivocada dejaba una traza en ERROR, que es
     * justo lo que esconde los errores de verdad.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> manejarNoEncontrado(Exception ex,
                                                                 HttpServletRequest request) {
        // En DEBUG y no en ERROR: una ruta equivocada es cosa del cliente, no
        // un fallo del servicio.
        log.debug("Ruta no encontrada: {} {}",
                request.getMethod(), request.getRequestURI());

        return respuestaSimple(request, HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO",
                "El recurso solicitado no existe.");
    }

    /**
     * Metodo HTTP que la ruta no admite.
     *
     * <p>Un POST a una ruta que solo lee es un 405, no un 500: el recurso
     * existe y el cliente puede corregirlo mirando el Allow de la respuesta.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> manejarMetodoNoAdmitido(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.debug("Metodo no admitido: {} {}",
                request.getMethod(), request.getRequestURI());

        return respuestaSimple(request, HttpStatus.METHOD_NOT_ALLOWED,
                "METODO_NO_ADMITIDO",
                "El metodo " + request.getMethod()
                        + " no esta permitido en esta ruta.");
    }

    /**
     * Ultimo recurso. Se registra en ERROR con la excepcion completa, porque es
     * el unico caso donde no se sabe que fallo, pero al cliente solo le llega
     * un mensaje generico y el requestId para poder reportarlo.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> manejarInesperado(Exception ex,
                                                               HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("[{}] Error inesperado en {}", requestId, request.getRequestURI(), ex);

        ErrorResponse error = ErrorResponse.of("ERROR_INTERNO",
                HttpStatus.INTERNAL_SERVER_ERROR.value(), request.getRequestURI(), requestId);

        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("Ocurrio un error inesperado. Reporta el codigo "
                        + requestId + " si el problema persiste.", error));
    }

    protected ResponseEntity<ApiResponse<Void>> respuestaSimple(HttpServletRequest request,
                                                                HttpStatus estado,
                                                                String codigo,
                                                                String mensaje) {
        String requestId = requestId(request);
        log.debug("[{}] {} en {}", requestId, codigo, request.getRequestURI());
        ErrorResponse error = ErrorResponse.of(codigo, estado.value(), request.getRequestURI(), requestId);
        return ResponseEntity.status(estado).body(ApiResponse.fail(mensaje, error));
    }

    protected String requestId(HttpServletRequest request) {
        return request.getHeader(GatewayHeaders.REQUEST_ID);
    }
}
