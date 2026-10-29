package com.medcore.gateway.security;

import com.medcore.commons.security.GatewayHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Construye la peticion que se reenvia al microservicio, con la identidad ya
 * resuelta en cabeceras (MC4).
 *
 * <p>Las cabeceras de identidad se <em>sobrescriben</em> siempre, nunca se
 * anaden: si un cliente externo enviara su propio {@code X-User-Role: ADMIN},
 * quedaria descartado aqui. Esa es la unica defensa del modelo de confianza,
 * junto con no exponer los microservicios fuera de la red interna.
 */
@Component
public class UserHeaderPropagator {

    /** Peticion autenticada: se fijan userId, rol y requestId. */
    public ServerHttpRequest conIdentidad(ServerHttpRequest request,
                                          String userId,
                                          String rol,
                                          String requestId) {
        return request.mutate()
                .headers(h -> {
                    h.set(GatewayHeaders.USER_ID, userId);
                    h.set(GatewayHeaders.USER_ROLE, rol);
                    h.set(GatewayHeaders.REQUEST_ID, requestId);
                })
                .build();
    }

    /**
     * Peticion a una ruta publica: no hay identidad, pero se propaga el
     * requestId y se borran las cabeceras de identidad que pudieran venir
     * desde fuera.
     */
    public ServerHttpRequest sinIdentidad(ServerHttpRequest request, String requestId) {
        return request.mutate()
                .headers(h -> {
                    h.remove(GatewayHeaders.USER_ID);
                    h.remove(GatewayHeaders.USER_ROLE);
                    h.set(GatewayHeaders.REQUEST_ID, requestId);
                })
                .build();
    }

    /**
     * Reutiliza el X-Request-Id entrante si existe, o genera uno nuevo.
     * Mantenerlo permite seguir una misma operacion a traves de varios
     * servicios en los logs.
     */
    public String resolverRequestId(ServerHttpRequest request) {
        String entrante = request.getHeaders().getFirst(GatewayHeaders.REQUEST_ID);
        return (entrante == null || entrante.isBlank()) ? UUID.randomUUID().toString() : entrante;
    }
}
