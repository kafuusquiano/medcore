package com.medcore.appointment.mapper;

import com.medcore.appointment.domain.dto.response.ScheduleResponse;
import com.medcore.appointment.domain.entity.DoctorSchedule;
import org.springframework.stereotype.Component;

/**
 * Conversion del horario a su vista.
 *
 * <p>Los dias se guardan como texto separado por comas y se exponen como
 * conjunto de {@code DayOfWeek}, para que el cliente no tenga que parsear
 * una cadena.
 */
@Component
public class ScheduleMapper {

    public ScheduleResponse aRespuesta(DoctorSchedule horario) {
        return new ScheduleResponse(
                horario.getId(),
                horario.getIdMedico(),
                horario.getNombreMedico(),
                horario.getEspecialidad(),
                horario.dias(),
                horario.getHoraInicio(),
                horario.getHoraFin(),
                horario.isActivo());
    }
}
