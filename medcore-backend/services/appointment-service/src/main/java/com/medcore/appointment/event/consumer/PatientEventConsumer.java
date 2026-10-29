package com.medcore.appointment.event.consumer;

import com.medcore.commons.event.PatientEvent;
import com.medcore.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Escucha {@code patient.created} y {@code patient.updated}.
 *
 * <p>No crea ni modifica nada: las citas guardan una copia del nombre y el
 * contacto tal como estaban al reservar, y esa copia no debe cambiar
 * retroactivamente. Si un paciente corrige su telefono hoy, la cita de la
 * semana pasada debe seguir mostrando con que datos se reservo.
 *
 * <p>Se mantiene el consumidor porque deja traza de los cambios en el log y
 * es el punto donde encajara la cache de pacientes si la agenda llega a
 * necesitarla. El offset se confirma siempre: un fallo aqui no debe bloquear
 * la particion.
 */
@Component
public class PatientEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientEventConsumer.class);

    @KafkaListener(topics = KafkaTopics.PATIENT_CREATED, groupId = "appointment-service")
    public void alCambiarPaciente(PatientEvent evento, Acknowledgment ack) {
        try {
            if (evento == null || evento.idPaciente() == null) {
                log.warn("Evento de paciente incompleto; se descarta");
                return;
            }
            log.debug("Recibido {} del paciente {} [{}]",
                    evento.eventType(), evento.idPaciente(), evento.requestId());

        } catch (RuntimeException e) {
            log.error("Error procesando el evento de paciente: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }
}
