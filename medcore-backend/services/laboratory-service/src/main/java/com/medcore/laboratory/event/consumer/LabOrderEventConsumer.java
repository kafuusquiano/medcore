package com.medcore.laboratory.event.consumer;

import com.medcore.commons.event.LabOrderEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.laboratory.service.LabOrderQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Escucha {@code lab-order.created}.
 *
 * <p>Deja la orden en la cola local del laboratorio. Es el unico camino por
 * el que una orden llega aqui: el laboratorio no consulta a medical-service
 * cuando el tubo aparece en el mostrador, porque entonces una caida de ese
 * servicio detendria la recepcion de muestras. Con la copia local el
 * laboratorio sigue trabajando.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como el metodo atrapa toda
 * RuntimeException, un fallo queda en el log y el offset avanza igualmente,
 * de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class LabOrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LabOrderEventConsumer.class);

    private final LabOrderQueueService colaService;

    public LabOrderEventConsumer(LabOrderQueueService colaService) {
        this.colaService = colaService;
    }

    @KafkaListener(topics = KafkaTopics.LAB_ORDER_CREATED, groupId = "laboratory-service")
    public void alCrearseOrden(LabOrderEvent evento) {
        try {
            if (evento == null || evento.idOrden() == null) {
                log.warn("Evento lab-order.created incompleto; se descarta");
                return;
            }

            // Una orden sin pruebas no deja nada que hacer al laboratorio y
            // apareceria en la cola como un tubo fantasma.
            if (evento.pruebas() == null || evento.pruebas().isEmpty()) {
                log.warn("La orden {} llego sin pruebas; no se encola [{}]",
                        evento.codigoOrden(), evento.requestId());
                return;
            }

            colaService.encolar(evento);

        } catch (RuntimeException e) {
            log.error("Error procesando lab-order.created de la orden {}: {}",
                    evento == null ? "desconocida" : evento.codigoOrden(), e.getMessage(), e);
        }
    }
}
