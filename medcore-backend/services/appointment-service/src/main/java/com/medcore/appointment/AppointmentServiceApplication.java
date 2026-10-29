package com.medcore.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * appointment-service (:8083): citas, horarios y disponibilidad.
 *
 * <p>Base propia appointment_db mas Redis para la cache de disponibilidad.
 * Consulta a patient-service por Feign y publica appointment.created y
 * appointment.cancelled.
 *
 * <p>EnableScheduling activa la generacion nocturna de slots y el cierre de
 * citas vencidas.
 */
@EnableFeignClients
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
