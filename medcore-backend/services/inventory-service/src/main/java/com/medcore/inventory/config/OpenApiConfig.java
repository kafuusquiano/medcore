package com.medcore.inventory.config;

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
    public OpenAPI inventoryServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - inventory-service")
                .version("1.0.0")
                .description("""
                        Reactivos, stock por lote y alertas de vencimiento.

                        El stock se lleva por lote con su fecha de
                        vencimiento: el mismo reactivo llega en varias remesas
                        y hay que gastar primero la que caduca antes.

                        La cantidad de un lote cambia solo registrando un
                        movimiento. No hay forma de editarla directamente, de
                        modo que el stock actual y el historial nunca pueden
                        discrepar. Un error se corrige con un AJUSTE que deja
                        constancia de la diferencia, nunca borrando un
                        movimiento.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
