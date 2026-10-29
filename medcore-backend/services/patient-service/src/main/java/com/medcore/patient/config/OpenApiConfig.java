package com.medcore.patient.config;

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
    public OpenAPI patientServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - patient-service")
                .version("1.0.0")
                .description("""
                        Fichas de pacientes e historial clinico.

                        La identidad llega en las cabeceras X-User-Id y
                        X-User-Role, que propaga el api-gateway tras validar
                        el JWT. Este servicio no recibe el token.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
