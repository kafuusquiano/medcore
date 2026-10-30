package com.medcore.laboratory.config;

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
    public OpenAPI laboratoryServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - laboratory-service")
                .version("1.0.0")
                .description("""
                        Muestras, resultados e interfaz HL7 con los equipos.

                        Un resultado validado no se modifica: corregirlo
                        consiste en registrar otro que lo rectifique, de modo
                        que el historial conserve lo que se informo en su
                        momento.

                        La interfaz HL7 en /api/hl7/oru no responde JSON sino
                        un ACK en texto plano, y nunca devuelve un error HTTP:
                        un equipo que no recibe ACK reintenta y acaba
                        bloqueando su cola de salida.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
