package com.medcore.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * patient-service (:8082): fichas de pacientes e historial clinico.
 *
 * <p>Base propia {@code patient_db}; ningun otro servicio la consulta
 * directamente (MC1). Publica {@code patient.created} y
 * {@code patient.updated}, que consumen auth-service, para crear la cuenta de
 * acceso del paciente, y appointment-service, para la agenda.
 */
@SpringBootApplication
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
