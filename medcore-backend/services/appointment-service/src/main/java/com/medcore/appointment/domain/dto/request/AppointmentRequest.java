package com.medcore.appointment.domain.dto.request;

import com.medcore.appointment.domain.enums.AppointmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reserva de una cita.
 *
 * <p>Se identifica el horario por {@code idSlot} y no por fecha y hora
 * sueltas: el cliente elige una franja de las que le devolvio la consulta de
 * disponibilidad, de modo que no puede inventar un horario que no exista ni
 * caer entre dos franjas.
 */
public record AppointmentRequest(

        @NotNull(message = "El paciente es obligatorio")
        Long idPaciente,

        @NotNull(message = "El horario es obligatorio")
        Long idSlot,

        @NotNull(message = "El tipo de cita es obligatorio")
        AppointmentType tipo,

        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
