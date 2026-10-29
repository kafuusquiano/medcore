package com.medcore.patient.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Map;

/**
 * Errores propios de patient-service.
 */
public class PatientException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PatientException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    public PatientException(String codigo, HttpStatus estado, String mensaje,
                            Map<String, Object> detalles) {
        super(codigo, estado, mensaje, detalles);
    }

    public static PatientException noEncontrado(Long id) {
        return new PatientException("PACIENTE_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe un paciente con el identificador indicado.",
                Map.of("id", id));
    }

    public static PatientException noEncontradoPorDni(String dni) {
        return new PatientException("PACIENTE_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe un paciente con el DNI " + dni + ".");
    }

    public static PatientException dniYaRegistrado(String dni) {
        return new PatientException("DNI_YA_REGISTRADO", HttpStatus.CONFLICT,
                "Ya existe un paciente registrado con el DNI " + dni + ".");
    }

    public static PatientException fichaCerrada() {
        return new PatientException("FICHA_CERRADA", HttpStatus.CONFLICT,
                "La ficha de un paciente fallecido no puede reactivarse.");
    }

    public static PatientException noAdmiteAtencion() {
        return new PatientException("PACIENTE_NO_ACTIVO", HttpStatus.CONFLICT,
                "La ficha del paciente no esta activa y no admite nuevas atenciones.");
    }

    public static PatientException rangoEdadInvalido() {
        return new PatientException("RANGO_EDAD_INVALIDO", HttpStatus.BAD_REQUEST,
                "La edad minima no puede ser mayor que la maxima.");
    }
}
