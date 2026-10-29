package com.medcore.appointment.event.consumer;

import com.medcore.commons.event.PatientEvent;
import com.medcore.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como el metodo atrapa toda
 * RuntimeException, un fallo queda en el log y el offset avanza igualmente,
 * de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class PatientEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientEventConsumer.class);

    @KafkaListener(topics = KafkaTopics.PATIENT_CREATED, groupId = "appointment-service")
    public void alCambiarPaciente(PatientEvent evento) {
        try {
            if (evento == null || evento.idPaciente() == null) {
                log.warn("Evento de paciente incompleto; se descarta");
                return;
            }
            log.debug("Recibido {} del paciente {} [{}]",
                    evento.eventType(), evento.idPaciente(), evento.requestId());

        } catch (RuntimeException e) {
            log.error("Error procesando el evento de paciente: {}", e.getMessage(), e);
        }
    }
}
