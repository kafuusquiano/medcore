package com.medcore.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Propiedades propias del gateway, bajo el prefijo {@code gateway}.
 *
 * <p>Se enlazan con {@code @ConfigurationProperties} y no con {@code @Value}
 * porque el config-server entrega las listas YAML como claves indexadas
 * ({@code gateway.rutas-publicas[0]}, {@code [1]}, ...). No existe una clave
 * plana {@code gateway.rutas-publicas}, asi que {@code @Value} falla con
 * PlaceholderResolutionException; solo el binder sabe reconstruir la lista.
 *
 * <p>Los valores reales llegan de {@code infra/config-repo/api-gateway.yml}.
 * Los defaults de aqui solo actuan si el config-server no responde, de modo
 * que el gateway arranque igualmente con una postura segura.
 *
 * @param rutasPublicas rutas que no exigen JWT
 * @param rateLimit     parametros de la cubeta de tokens
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(

        @DefaultValue({"/api/auth/login", "/api/auth/refresh", "/actuator/health"})
        List<String> rutasPublicas,

        @DefaultValue
        RateLimit rateLimit
) {

    /**
     * @param replenishRate   peticiones por segundo que se reponen
     * @param burstCapacity   pico maximo admitido
     * @param requestedTokens tokens que consume cada peticion
     */
    public record RateLimit(
            @DefaultValue("20") int replenishRate,
            @DefaultValue("40") int burstCapacity,
            @DefaultValue("1") int requestedTokens
    ) {
    }
}
