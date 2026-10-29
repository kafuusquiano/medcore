package com.medcore.appointment.event.producer;

import com.medcore.appointment.domain.entity.Appointment;
import com.medcore.commons.event.AppointmentEvent;
import com.medcore.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos del ciclo de vida de la cita.
 *
 * <p>{@code appointment.created} lo consume notification-service para avisar
 * al paciente. {@code appointment.cancelled} lo consumen ademas
 * medical-service, que descarta la atencion prevista.
 *
 * <p>La clave de particion es el id del paciente, para que sus eventos
 * lleguen en orden: sin eso, una cancelacion podria procesarse antes que la
 * creacion de la misma cita y el paciente recibiria el aviso al reves.
 *
 * <p>Un fallo al publicar no revierte la reserva: la cita ya existe y eso es
 * lo que importaba. El error queda en el log para reponerlo a mano.
 */
@Component
public class AppointmentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventProducer.class);

    private static final String TIPO_CREADA = "appointment.created";
    private static final String TIPO_CANCELADA = "appointment.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AppointmentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarCreada(Appointment cita, String requestId) {
        publicar(KafkaTopics.APPOINTMENT_CREATED, TIPO_CREADA, cita, requestId);
    }

    public void publicarCancelada(Appointment cita, String requestId) {
        publicar(KafkaTopics.APPOINTMENT_CANCELLED, TIPO_CANCELADA, cita, requestId);
    }

    private void publicar(String topico, String tipoEvento, Appointment cita, String requestId) {
        AppointmentEvent evento = AppointmentEvent.de(
                tipoEvento,
                requestId,
                cita.getId(),
                cita.getIdPaciente(),
                cita.getIdMedico(),
                cita.getDniPaciente(),
                cita.getNombrePaciente(),
                cita.getCorreoPaciente(),
                cita.getTelefonoPaciente(),
                cita.getFechaHora(),
                cita.getTipo().name(),
                cita.getEstado().name(),
                cita.getMotivoCancelacion());

        kafkaTemplate.send(topico, String.valueOf(cita.getIdPaciente()), evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar {} de la cita {}: {}",
                                tipoEvento, cita.getId(), error.getMessage());
                    } else {
                        log.debug("Publicado {} de la cita {} en la particion {}",
                                tipoEvento, cita.getId(),
                                resultado.getRecordMetadata().partition());
                    }
                });
    }
}
