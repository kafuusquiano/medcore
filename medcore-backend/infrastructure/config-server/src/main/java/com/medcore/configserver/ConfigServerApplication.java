package com.medcore.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Configuracion centralizada de MedCore (:8888).
 *
 * <p>Sirve a cada microservicio su archivo {@code {servicio}.yml} mas el
 * {@code application.yml} comun, siguiendo la regla MC3.
 *
 * <p>Decision del proyecto: funciona siempre en modo <em>native</em>, leyendo
 * {@code infra/config-repo/} desde disco. No existe un repositorio
 * {@code medcore-config} aparte, de modo que no hace falta token de GitHub ni
 * hay riesgo de que su expiracion deje al sistema sin configuracion.
 *
 * <p>Arranca en el nivel 3 de MC2: depende de que eureka-server este sano.
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
