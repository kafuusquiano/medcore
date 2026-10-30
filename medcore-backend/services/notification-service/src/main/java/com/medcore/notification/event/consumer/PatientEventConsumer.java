package com.medcore.notification.event.consumer;

import com.medcore.commons.event.PatientEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Escucha {@code patient.created}.
 *
 * <p>Envia el correo de bienvenida. No se avisa de patient.updated: un cambio
 * de telefono no merece un correo, y mandarlo acostumbraria al paciente a
 * ignorar los avisos de MedCore, que es lo contrario de lo que se busca.
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

    private final NotificationService notificationService;

    public PatientEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopics.PATIENT_CREATED, groupId = "notification-service")
    public void alCrearsePaciente(PatientEvent evento) {
        try {
            if (evento == null || evento.idPaciente() == null) {
                log.warn("Evento patient.created incompleto; se descarta");
                return;
            }

            Map<String, String> datos = new HashMap<>();
            datos.put("nombrePaciente", evento.nombreCompleto());
            datos.put("dniPaciente", evento.dni());

            notificationService.crear(
                    NotificationType.PACIENTE_REGISTRADO,
                    evento.eventId(),
                    evento.correo(),
                    evento.idPaciente(),
                    null,
                    datos);

        } catch (RuntimeException e) {
            log.error("Error procesando patient.created del paciente {}: {}",
                    evento == null ? "desconocido" : evento.idPaciente(), e.getMessage(), e);
        }
    }
}
