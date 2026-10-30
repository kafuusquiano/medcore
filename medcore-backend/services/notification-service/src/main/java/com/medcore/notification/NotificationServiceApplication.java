package com.medcore.notification;

import com.medcore.notification.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * notification-service (:8088): avisos por correo y en la aplicacion.
 *
 * <p>Base propia notification_db (MC1). Es el consumidor con mas topicos del
 * sistema, nueve, y no publica ninguno: es el extremo de todas las cadenas de
 * eventos.
 *
 * <p>Los avisos nacen SOLO de eventos. No hay endpoint para crearlos: si se
 * pudieran crear por peticion, cualquier cliente podria mandar un correo en
 * nombre de la clinica, y el aviso dejaria de corresponder con un hecho
 * ocurrido en el sistema.
 *
 * <p>@EnableScheduling por la pasada periodica de la cola, que recoge lo que
 * fallo y lo programado, como el recordatorio de cita del dia siguiente.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
