package com.medcore.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.ErrorResponse;
import com.medcore.commons.security.GatewayHeaders;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


/**
 * Filtro global que autentica toda peticion entrante (MC4).
 *
 * <p>Flujo: si la ruta es publica se deja pasar sin identidad; si no, se exige
 * un Bearer valido, se extraen userId y rol, y se reenvia al microservicio con
 * las cabeceras {@code X-User-Id}, {@code X-User-Role} y {@code X-Request-Id}.
 *
 * <p>Se ejecuta antes que el enrutamiento ({@code HIGHEST_PRECEDENCE + 1}) para
 * que ninguna peticion llegue a un servicio sin pasar por aqui.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String PREFIJO_BEARER = "Bearer ";

    /**
     * Atributo del exchange donde se deja el requestId. El
     * GatewayExceptionHandler recibe el exchange original, no el mutado por
     * este filtro, asi que no veria la cabecera X-Request-Id si no se
     * guardara tambien aqui.
     */
    public static final String ATTR_REQUEST_ID = "medcore.requestId";

    private final JwtUtil jwtUtil;
    private final RouteValidator routeValidator;
    private final UserHeaderPropagator propagator;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   RouteValidator routeValidator,
                                   UserHeaderPropagator propagator,
                                   ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.routeValidator = routeValidator;
        this.propagator = propagator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = propagator.resolverRequestId(request);
        exchange.getAttributes().put(ATTR_REQUEST_ID, requestId);

        if (!routeValidator.requiereAutenticacion(request)) {
            ServerHttpRequest publica = propagator.sinIdentidad(request, requestId);
            return chain.filter(exchange.mutate().request(publica).build());
        }

        String token = extraerToken(request);
        if (token == null) {
            return rechazar(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_AUSENTE",
                    "Se requiere un token de acceso para esta operacion.", requestId);
        }

        try {
            Claims claims = jwtUtil.validarYLeer(token);
            String userId = jwtUtil.extraerUserId(claims);
            String rol = jwtUtil.extraerRol(claims);

            if (userId == null || userId.isBlank() || rol == null || rol.isBlank()) {
                return rechazar(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INCOMPLETO",
                        "El token no contiene la identidad requerida.", requestId);
            }

            ServerHttpRequest autenticada = propagator.conIdentidad(request, userId, rol, requestId);
            return chain.filter(exchange.mutate().request(autenticada).build());

        } catch (ExpiredJwtException e) {
            return rechazar(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRADO",
                    "La sesion expiro. Inicia sesion nuevamente.", requestId);
        } catch (JwtException | IllegalArgumentException e) {
            return rechazar(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO",
                    "El token de acceso no es valido.", requestId);
        }
    }

    private String extraerToken(ServerHttpRequest request) {
        String cabecera = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(PREFIJO_BEARER)) {
            return null;
        }
        String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** Responde con el wrapper ApiResponse (G2), sin stack trace (G3). */
    private Mono<Void> rechazar(ServerWebExchange exchange, HttpStatus estado,
                                String codigo, String mensaje, String requestId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(estado);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(GatewayHeaders.REQUEST_ID, requestId);

        String ruta = exchange.getRequest().getURI().getPath();
        log.debug("Peticion rechazada [{}] {} {}", requestId, codigo, ruta);

        ApiResponse<Void> cuerpo = ApiResponse.fail(
                mensaje,
                ErrorResponse.of(codigo, estado.value(), ruta, requestId));

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(cuerpo);
        } catch (Exception e) {
            // Si ni siquiera se puede serializar el error, se responde sin cuerpo
            // antes que propagar una excepcion dentro del filtro.
            log.warn("No se pudo serializar la respuesta de error [{}]", requestId);
            return response.setComplete();
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
