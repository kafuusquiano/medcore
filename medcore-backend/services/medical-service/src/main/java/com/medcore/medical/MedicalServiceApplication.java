package com.medcore.medical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * medical-service (:8084): atenciones, diagnosticos CIE-10 y ordenes de
 * laboratorio.
 *
 * <p>Base propia medical_db (MC1). Consume appointment.cancelled para anular
 * las atenciones de citas canceladas, y publica lab-order.created, que abre
 * el flujo del laboratorio.
 */
@SpringBootApplication
public class MedicalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalServiceApplication.class, args);
    }
}
