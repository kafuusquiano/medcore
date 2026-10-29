package com.medcore.patient.event.producer;

import com.medcore.commons.event.PatientEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.patient.domain.entity.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos del ciclo de vida del paciente.
 *
 * <p>Consumidores: auth-service crea la cuenta de acceso con rol PACIENTE y
 * appointment-service guarda los datos minimos para la agenda.
 *
 * <p>Se publica con el DNI como clave de particion para que todos los eventos
 * de un mismo paciente vayan a la misma particion y lleguen en orden. Sin eso,
 * un patient.updated podria procesarse antes que el patient.created.
 *
 * <p>Un fallo al publicar no revierte el alta: el paciente ya quedo
 * registrado, y eso es lo que importaba. El error queda en el log para
 * reponer el evento a mano.
 */
@Component
public class PatientEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PatientEventProducer.class);

    private static final String TIPO_CREADO = "patient.created";
    private static final String TIPO_ACTUALIZADO = "patient.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PatientEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarCreado(Patient paciente, String requestId) {
        publicar(KafkaTopics.PATIENT_CREATED, TIPO_CREADO, paciente, requestId);
    }

    public void publicarActualizado(Patient paciente, String requestId) {
        // Se reutiliza el topico patient.created porque la guia solo define
        // nueve topicos y los consumidores distinguen por eventType.
        publicar(KafkaTopics.PATIENT_CREATED, TIPO_ACTUALIZADO, paciente, requestId);
    }

    private void publicar(String topico, String tipoEvento, Patient paciente, String requestId) {
        PatientEvent evento = PatientEvent.creado(
                tipoEvento,
                requestId,
                paciente.getId(),
                paciente.getDni(),
                paciente.getNombres(),
                paciente.getApellidos(),
                paciente.getFechaNacimiento(),
                paciente.getCorreo(),
                paciente.getTelefono());

        kafkaTemplate.send(topico, paciente.getDni(), evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar {} del paciente {}: {}",
                                tipoEvento, paciente.getId(), error.getMessage());
                    } else {
                        log.debug("Publicado {} del paciente {} en la particion {}",
                                tipoEvento, paciente.getId(),
                                resultado.getRecordMetadata().partition());
                    }
                });
    }
}
