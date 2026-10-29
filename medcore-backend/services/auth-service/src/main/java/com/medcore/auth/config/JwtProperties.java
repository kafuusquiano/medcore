package com.medcore.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametros de emision de JWT, bajo el prefijo {@code jwt}.
 * Los valores llegan de infra/config-repo/application.yml.
 *
 * @param secret              clave de firma; minimo 64 caracteres para HS512
 * @param expirationMs        vigencia del token de acceso
 * @param refreshExpirationMs vigencia del token de refresco
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("3600000") long expirationMs,
        @DefaultValue("604800000") long refreshExpirationMs
) {
    public long expiracionSegundos() {
        return expirationMs / 1000;
    }
}
