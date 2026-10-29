package com.medcore.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Datos del ADMIN inicial, bajo el prefijo {@code auth.admin}.
 *
 * <p>La contrasena no tiene valor por defecto a proposito: si no se define
 * ADMIN_INITIAL_PASSWORD, {@link AdminBootstrap} genera una aleatoria en vez
 * de usar una conocida.
 */
@ConfigurationProperties(prefix = "auth.admin")
public record AdminSeedProperties(
        @DefaultValue("00000001") String dni,
        @DefaultValue("admin@medcore.local") String correo,
        @DefaultValue("Administrador") String nombres,
        @DefaultValue("MedCore") String apellidos,
        String password
) {
}
