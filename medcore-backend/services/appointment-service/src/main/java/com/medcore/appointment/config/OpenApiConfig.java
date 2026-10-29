package com.medcore.appointment.config;

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
    public OpenAPI appointmentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - appointment-service")
                .version("1.0.0")
                .description("Citas, horarios de medicos y disponibilidad de franjas de 30 minutos.")
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
