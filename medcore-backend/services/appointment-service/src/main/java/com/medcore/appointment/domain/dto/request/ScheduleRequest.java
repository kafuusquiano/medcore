package com.medcore.appointment.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * Alta o actualizacion del horario de atencion de un medico.
 *
 * @param dias dias de la semana en nombres de DayOfWeek: MONDAY, TUESDAY...
 */
public record ScheduleRequest(

        @NotNull(message = "El medico es obligatorio")
        Long idMedico,

        @NotBlank(message = "El nombre del medico es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombreMedico,

        @Size(max = 80, message = "La especialidad no puede superar los 80 caracteres")
        String especialidad,

        @NotEmpty(message = "Debe indicarse al menos un dia de atencion")
        Set<DayOfWeek> dias,

        @NotNull(message = "La hora de inicio es obligatoria")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horaInicio,

        @NotNull(message = "La hora de fin es obligatoria")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horaFin
) {
    /**
     * La coherencia del rango se valida aqui y no con una anotacion, porque
     * depende de dos campos a la vez.
     */
    public boolean rangoHorarioValido() {
        return horaInicio != null && horaFin != null && horaFin.isAfter(horaInicio);
    }
}
