package com.medcore.appointment.mapper;

import com.medcore.appointment.domain.dto.response.AppointmentResponse;
import com.medcore.appointment.domain.entity.Appointment;
import org.springframework.stereotype.Component;

/**
 * Conversion de la cita a su vista.
 *
 * <p>Se escribe a mano y no con MapStruct porque {@code puedeCancelarse}
 * depende de un parametro externo, la antelacion minima configurada, y
 * MapStruct no encaja bien con un campo derivado de un valor que no esta en
 * la entidad.
 */
@Component
public class AppointmentMapper {

    public AppointmentResponse aRespuesta(Appointment cita, int horasAntelacionMinima) {
        boolean puedeCancelarse = cita.estaActiva()
                && cita.horasDeAntelacion() >= horasAntelacionMinima;

        return new AppointmentResponse(
                cita.getId(),
                cita.getIdPaciente(),
                cita.getDniPaciente(),
                cita.getNombrePaciente(),
                cita.getIdMedico(),
                cita.getNombreMedico(),
                cita.getFechaHora(),
                cita.getTipo(),
                cita.getEstado(),
                cita.getMotivo(),
                cita.getMotivoCancelacion(),
                puedeCancelarse,
                cita.getFechaCreacion());
    }
}
