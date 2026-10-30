package com.medcore.analytics.config;

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
    public OpenAPI analyticsServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - analytics-service")
                .version("1.0.0")
                .description("""
                        Epidemiologia, deteccion de brotes e indicadores.

                        La vigilancia es por PRUEBA DE LABORATORIO con
                        resultado anormal y no por codigo CIE-10:
                        medical-service todavia no publica eventos de
                        diagnostico, y analytics solo recibe resultados. Cinco
                        casos de la misma prueba alterada en una zona en siete
                        dias es una senal valida por si misma.

                        Se cuentan pacientes distintos y no resultados: a una
                        persona a la que se repite la prueba tres veces en la
                        semana no se la puede contar como tres casos.

                        Los informes se calculan en vivo y pueden cambiar si un
                        resultado se corrige despues. Para conservar el valor
                        con el que se tomo una decision estan las fotos de
                        indicadores.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
