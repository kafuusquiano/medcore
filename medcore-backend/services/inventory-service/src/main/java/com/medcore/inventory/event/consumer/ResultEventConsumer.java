package com.medcore.inventory.event.consumer;

import com.medcore.commons.event.ResultEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.inventory.service.AutomaticConsumptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume result.registered para descontar el reactivo gastado.
 *
 * <p>Un resultado registrado es una determinacion hecha, y una determinacion
 * hecha es reactivo gastado. Es lo que mantiene el stock del sistema pegado al
 * real: antes habia que apuntar el consumo a mano, y se separaban en cuanto
 * alguien se olvidaba.
 *
 * <p>Por evento y no por una llamada del laboratorio a proposito. Si fuera una
 * llamada sincrona, un inventory-service caido bloquearia el procesamiento de
 * las muestras, y eso en un laboratorio no es negociable. Asi los mensajes
 * esperan en Kafka y el laboratorio sigue trabajando.
 *
 * <p>Solo escucha {@code result.registered} y NO {@code result.critical}: un
 * valor critico se publica en los dos topicos, y escuchar los dos descontaria
 * el reactivo dos veces. La idempotencia por idResultado lo atraparia, pero
 * depender de ella para algo evitable es pedir problemas.
 */
@Component
public class ResultEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultEventConsumer.class);

    private final AutomaticConsumptionService consumptionService;

    public ResultEventConsumer(AutomaticConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @KafkaListener(topics = KafkaTopics.RESULT_REGISTERED, groupId = "inventory-service")
    public void alRegistrarseResultado(ResultEvent evento) {
        try {
            if (evento == null) {
                log.warn("Evento de resultado nulo; se descarta");
                return;
            }

            var descuentos = consumptionService.descontarPor(evento);

            if (!descuentos.isEmpty()) {
                log.debug("Resultado {} ({}): {} reactivo(s) descontado(s) [{}]",
                        evento.idResultado(), evento.codigoPrueba(),
                        descuentos.size(), evento.requestId());
            }

        } catch (RuntimeException e) {
            // Se atrapa todo: un descuadre del almacen no puede bloquear la
            // particion y dejar sin procesar los resultados siguientes. El
            // stock se arregla a mano; un resultado perdido, no.
            log.error("Error descontando el reactivo del resultado {}: {}",
                    evento == null ? "desconocido" : evento.idResultado(),
                    e.getMessage(), e);
        }
    }
}
