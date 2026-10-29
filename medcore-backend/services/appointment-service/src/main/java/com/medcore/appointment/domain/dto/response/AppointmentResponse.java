package com.medcore.appointment.domain.dto.response;

import com.medcore.appointment.domain.enums.AppointmentStatus;
import com.medcore.appointment.domain.enums.AppointmentType;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Cita tal como la ve el cliente.
 *
 * @param puedeCancelarse indica si aun esta dentro del plazo de cancelacion,
 *                        para que el frontend muestre o no el boton
 */
public record AppointmentResponse(
        Long id,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,
        Long idMedico,
        String nombreMedico,
        LocalDateTime fechaHora,
        AppointmentType tipo,
        AppointmentStatus estado,
        String motivo,
        String motivoCancelacion,
        boolean puedeCancelarse,
        Instant fechaCreacion
) {
}
