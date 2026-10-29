package com.medcore.patient.domain.dto.response;

import com.medcore.patient.domain.enums.BloodType;
import com.medcore.patient.domain.enums.Gender;
import com.medcore.patient.domain.enums.PatientStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ficha completa del paciente.
 *
 * @param edad          anos cumplidos, calculados al responder
 * @param menorDeEdad   util para que el frontend exija datos del apoderado
 * @param tipoSangreEtiqueta forma legible del grupo sanguineo, por ejemplo "O+"
 */
public record PatientResponse(
        Long id,
        String dni,
        String nombres,
        String apellidos,
        String nombreCompleto,
        LocalDate fechaNacimiento,
        int edad,
        boolean menorDeEdad,
        Gender sexo,
        BloodType tipoSangre,
        String tipoSangreEtiqueta,
        String telefono,
        String correo,
        String direccion,
        String zona,
        String contactoEmergenciaNombre,
        String contactoEmergenciaTelefono,
        String alergias,
        PatientStatus estado,
        Instant fechaCreacion,
        Instant fechaActualizacion
) {
}
