package com.medcore.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * auth-service (:8081): autenticacion, emision de JWT y gestion de usuarios.
 *
 * <p>Es la base de la seguridad del sistema: emite los tokens que el
 * api-gateway valida en cada peticion.
 *
 * <p>Base propia auth_db, mas Redis para la lista negra de tokens revocados
 * (MC1: ningun otro servicio accede a esta base).
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
