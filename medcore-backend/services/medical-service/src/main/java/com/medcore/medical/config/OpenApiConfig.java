package com.medcore.medical.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacion OpenAPI del servicio (regla G7). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI medicalServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - medical-service")
                .version("1.0.0")
                .description("""
                        Atenciones medicas, diagnosticos CIE-10 y ordenes de laboratorio.

                        Una atencion finalizada no admite cambios: es un
                        registro clinico. Para corregirla se anula y se
                        registra otra.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
