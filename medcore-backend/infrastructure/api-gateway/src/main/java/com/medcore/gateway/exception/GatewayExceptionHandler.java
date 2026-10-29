package com.medcore.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.ErrorResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.gateway.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Traduce a {@link ApiResponse} cualquier error que no venga de un
 * microservicio, sino del propio enrutamiento (regla G3).
 *
 * <p>Los casos tipicos son un servicio que no esta registrado en Eureka, uno
 * que no responde a tiempo, y la cuota de peticiones agotada.
 *
 * <p>Nunca se devuelve el stack trace al cliente: el detalle tecnico queda en
 * el log, asociado al {@code X-Request-Id}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            // La respuesta ya empezo a escribirse; no se puede reemplazar.
            return Mono.error(ex);
        }

        String ruta = exchange.getRequest().getURI().getPath();
        // El filtro deja el requestId como atributo porque aqui llega el
        // exchange original, sin la cabecera que aquel anadio.
        String requestId = (String) exchange.getAttributes()
                .getOrDefault(JwtAuthenticationFilter.ATTR_REQUEST_ID,
                        exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID));

        HttpStatus estado;
        String codigo;
        String mensaje;

        if (ex instanceof ResponseStatusException rse) {
            estado = HttpStatus.valueOf(rse.getStatusCode().value());
            if (estado == HttpStatus.TOO_MANY_REQUESTS) {
                codigo = "LIMITE_PETICIONES";
                mensaje = "Demasiadas peticiones. Intenta nuevamente en unos segundos.";
            } else if (estado == HttpStatus.NOT_FOUND) {
                codigo = "RUTA_NO_ENCONTRADA";
                mensaje = "La ruta solicitada no existe.";
            } else if (estado == HttpStatus.SERVICE_UNAVAILABLE) {
                // Spring Cloud LoadBalancer lanza 503 cuando Eureka no tiene
                // ninguna instancia viva del servicio destino.
                codigo = "SERVICIO_NO_DISPONIBLE";
                mensaje = "El servicio no esta disponible en este momento. Intenta mas tarde.";
            } else {
                codigo = "ERROR_GATEWAY";
                mensaje = "No se pudo procesar la peticion.";
            }
        } else if (ex instanceof ConnectException || ex instanceof TimeoutException) {
            estado = HttpStatus.SERVICE_UNAVAILABLE;
            codigo = "SERVICIO_NO_DISPONIBLE";
            mensaje = "El servicio no esta disponible en este momento. Intenta mas tarde.";
        } else {
            estado = HttpStatus.INTERNAL_SERVER_ERROR;
            codigo = "ERROR_INTERNO";
            mensaje = "Ocurrio un error inesperado.";
        }

        if (estado.is5xxServerError()) {
            log.error("Error en el gateway [{}] {} {}", requestId, ruta, ex.toString());
        } else {
            log.debug("Peticion no atendida [{}] {} {}", requestId, codigo, ruta);
        }

        response.setStatusCode(estado);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> cuerpo = ApiResponse.fail(
                mensaje,
                ErrorResponse.of(codigo, estado.value(), ruta, requestId));

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(cuerpo);
        } catch (Exception e) {
            return response.setComplete();
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(buffer));
    }
}
