package com.medcore.notification.config;

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
    public OpenAPI notificationServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedCore - notification-service")
                .version("1.0.0")
                .description("""
                        Avisos por correo y en la aplicacion.

                        No hay forma de crear un aviso por la API: nacen de
                        eventos. Si se pudieran crear por peticion, cualquier
                        cliente podria mandar un correo en nombre de la
                        clinica.

                        Un valor critico se avisa al MEDICO con el valor
                        completo, y al paciente solo se le dice que su
                        resultado esta disponible. Un resultado alarmante por
                        correo, sin nadie que lo interprete, es peor que no
                        avisar.

                        Los canales PUSH y SMS estan declarados pero no
                        implementados: Firebase necesita un proyecto con
                        credenciales y el SMS una pasarela con contrato. Un
                        envio por esos canales se marca OMITIDO con el motivo,
                        en lugar de simular que salio.
                        """)
                .contact(new Contact().name("MedCore").email("soporte@medcore.local"))
                .license(new License().name("MIT")));
    }
}
