package com.medcore.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Registro de servicios de MedCore (:8761).
 *
 * <p>Es el primer componente de Spring Cloud en arrancar (nivel 2 de MC2):
 * config-server y api-gateway dependen de que este sano antes de iniciar.
 *
 * <p>No tiene base de datos. El registro vive en memoria y se reconstruye solo
 * cuando los servicios vuelven a enviar su heartbeat.
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
