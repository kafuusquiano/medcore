package com.medcore.auth.event.consumer;

import com.medcore.auth.service.UserService;
import com.medcore.commons.event.PatientEvent;
import com.medcore.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Crea la cuenta de acceso del paciente al recibir patient.created.
 *
 * <p>Kafka entrega al menos una vez, asi que el mismo evento puede llegar
 * repetido; la creacion es idempotente y descarta los duplicados.
 *
 * <p>El offset se confirma siempre, incluso si el procesamiento falla. Con
 * ack-mode: record, no confirmarlo haria que el consumidor reintentara el
 * mismo mensaje indefinidamente y bloqueara la particion. Un fallo aqui queda
 * en el log para tratarlo a mano, no detiene la cola.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como el metodo atrapa toda
 * RuntimeException, un fallo queda en el log y el offset avanza igualmente,
 * de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class PatientCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientCreatedConsumer.class);

    private final UserService userService;

    public PatientCreatedConsumer(UserService userService) {
        this.userService = userService;
    }

    @KafkaListener(topics = KafkaTopics.PATIENT_CREATED, groupId = "auth-service")
    public void alCrearsePaciente(PatientEvent evento) {
        try {
            if (evento == null || evento.idPaciente() == null || evento.dni() == null) {
                log.warn("Evento patient.created incompleto; se descarta");
                return;
            }

            log.debug("Recibido {} para el paciente {}", evento.eventType(), evento.idPaciente());

            userService.crearCuentaPaciente(
                    evento.idPaciente(),
                    evento.dni(),
                    evento.correo(),
                    evento.nombres(),
                    evento.apellidos(),
                    evento.telefono());

        } catch (RuntimeException e) {
            log.error("No se pudo crear la cuenta del paciente {}: {}",
                    evento == null ? null : evento.idPaciente(), e.getMessage(), e);
        }
    }
}
