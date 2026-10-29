package com.medcore.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametros propios de auth-service, bajo el prefijo {@code auth}.
 * Los valores llegan de infra/config-repo/auth-service.yml.
 *
 * @param maxIntentosFallidos intentos consecutivos antes de bloquear la cuenta
 * @param blacklistPrefix     prefijo de las claves de tokens revocados en Redis
 * @param bcryptStrength      coste de BCrypt; cada unidad duplica el tiempo
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        @DefaultValue("5") int maxIntentosFallidos,
        @DefaultValue("medcore:auth:blacklist:") String blacklistPrefix,
        @DefaultValue("10") int bcryptStrength
) {
}
