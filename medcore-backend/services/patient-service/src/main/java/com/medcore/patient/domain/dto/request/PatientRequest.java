package com.medcore.patient.domain.dto.request;

import com.medcore.patient.domain.enums.BloodType;
import com.medcore.patient.domain.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta o actualizacion de una ficha de paciente.
 *
 * <p>El DNI solo se usa al crear: en una actualizacion se ignora, porque
 * cambiarlo equivaldria a sustituir a la persona de la ficha y dejaria su
 * historial clinico atribuido a otra.
 */
public record PatientRequest(

        @NotBlank(message = "El DNI es obligatorio")
        @Pattern(regexp = "^[0-9]{8}$", message = "El DNI debe tener exactamente 8 digitos")
        String dni,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres")
        String apellidos,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
        LocalDate fechaNacimiento,

        @NotNull(message = "El sexo es obligatorio")
        Gender sexo,

        BloodType tipoSangre,

        @Pattern(regexp = "^$|^[0-9+ -]{6,15}$", message = "El telefono no tiene un formato valido")
        String telefono,

        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
        String correo,

        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccion,

        @Size(max = 80, message = "La zona no puede superar los 80 caracteres")
        String zona,

        @Size(max = 120, message = "El nombre del contacto no puede superar los 120 caracteres")
        String contactoEmergenciaNombre,

        @Pattern(regexp = "^$|^[0-9+ -]{6,15}$", message = "El telefono de emergencia no tiene un formato valido")
        String contactoEmergenciaTelefono,

        @Size(max = 500, message = "Las alergias no pueden superar los 500 caracteres")
        String alergias
) {
}
