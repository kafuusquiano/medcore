package com.medcore.commons.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento publicado por appointment-service en {@code appointment.created} y
 * {@code appointment.cancelled}.
 *
 * <p>Lo consume notification-service, que avisa al paciente, y en el caso de
 * la cancelacion tambien medical-service, que descarta la atencion prevista.
 *
 * <p>Lleva el nombre y el contacto del paciente ya resueltos para que
 * notification-service pueda redactar el mensaje sin volver a consultar a
 * patient-service: un evento que obliga a otra llamada no ahorra acoplamiento.
 *
 * @param motivoCancelacion nulo salvo en appointment.cancelled
 */
public record AppointmentEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idCita,
        Long idPaciente,
        Long idMedico,
        String dniPaciente,
        String nombrePaciente,
        String correoPaciente,
        String telefonoPaciente,
        LocalDateTime fechaHora,
        String tipoCita,
        String estado,
        String motivoCancelacion
) implements BaseEvent {

    public static AppointmentEvent de(String tipoEvento, String requestId, Long idCita,
                                      Long idPaciente, Long idMedico, String dniPaciente,
                                      String nombrePaciente, String correoPaciente,
                                      String telefonoPaciente, LocalDateTime fechaHora,
                                      String tipoCita, String estado, String motivoCancelacion) {
        return new AppointmentEvent(
                UUID.randomUUID().toString(),
                tipoEvento,
                Instant.now(),
                "appointment-service",
                requestId,
                idCita, idPaciente, idMedico, dniPaciente, nombrePaciente,
                correoPaciente, telefonoPaciente, fechaHora, tipoCita, estado,
                motivoCancelacion);
    }
}
