package com.medcore.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion OpenAPI del servicio (regla G7).
 * El gateway agrega la de todos los servicios en un unico Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - auth-service")
                .version("1.0.0")
                .description("""
                        Autenticacion, emision de JWT y gestion de usuarios y roles.

                        Las operaciones protegidas no reciben el token directamente:
                        el api-gateway lo valida y propaga la identidad en las
                        cabeceras X-User-Id y X-User-Role.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
