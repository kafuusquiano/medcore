package com.medcore.appointment.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Franja de la agenda tal como la ve el cliente.
 *
 * <p>No expone idCita: quien consulta la disponibilidad solo necesita saber
 * si el horario esta libre, no quien lo ocupa.
 */
public record SlotResponse(
        Long id,
        Long idMedico,
        LocalDate fecha,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
        boolean disponible
) {
}
