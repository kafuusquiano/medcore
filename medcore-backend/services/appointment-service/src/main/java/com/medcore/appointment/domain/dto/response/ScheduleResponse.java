package com.medcore.appointment.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/** Horario de atencion de un medico. */
public record ScheduleResponse(
        Long id,
        Long idMedico,
        String nombreMedico,
        String especialidad,
        Set<DayOfWeek> dias,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
        boolean activo
) {
}
