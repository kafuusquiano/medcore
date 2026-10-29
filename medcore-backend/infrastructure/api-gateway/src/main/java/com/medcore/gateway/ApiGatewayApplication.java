package com.medcore.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada unico de MedCore (:8080).
 *
 * <p>Es el unico puerto que se publica al host y el unico que atraviesa el
 * tunel hacia internet. Valida el JWT una sola vez, propaga la identidad en
 * cabeceras (MC4) y aplica CORS y limitacion de tasa.
 *
 * <p>Arranca en el nivel 4 de MC2: depende de que config-server este sano.
 *
 * <p>Es una aplicacion reactiva (WebFlux). No debe entrar en su classpath
 * {@code spring-boot-starter-web}: si lo hace, Spring arranca en modo servlet
 * y el gateway deja de enrutar. Por eso los starters de medcore-commons estan
 * declarados como opcionales.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
