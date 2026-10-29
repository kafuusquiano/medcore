package com.medcore.medical.event.producer;

import com.medcore.commons.event.LabOrderEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.medical.domain.entity.LabOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica lab-order.created.
 *
 * <p>Lo consumen laboratory-service, que abre la orden y espera las muestras,
 * y notification-service, que avisa al paciente de que debe acudir.
 *
 * <p>La clave de particion es el id del paciente, para que sus ordenes
 * lleguen en orden al laboratorio.
 */
@Component
public class LabOrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LabOrderEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LabOrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarCreada(LabOrder orden, String requestId) {
        var atencion = orden.getAtencion();

        LabOrderEvent evento = LabOrderEvent.creada(
                requestId,
                orden.getId(),
                orden.getCodigo(),
                atencion.getId(),
                orden.getIdPaciente(),
                atencion.getDniPaciente(),
                atencion.getNombrePaciente(),
                atencion.getCorreoPaciente(),
                orden.getIdMedico(),
                atencion.getNombreMedico(),
                orden.getPrioridad().name(),
                orden.getObservaciones(),
                orden.codigosDePruebas());

        kafkaTemplate.send(KafkaTopics.LAB_ORDER_CREATED,
                        String.valueOf(orden.getIdPaciente()), evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar lab-order.created de la orden {}: {}",
                                orden.getCodigo(), error.getMessage());
                    } else {
                        log.debug("Publicado lab-order.created de la orden {} en la particion {}",
                                orden.getCodigo(), resultado.getRecordMetadata().partition());
                    }
                });
    }
}
