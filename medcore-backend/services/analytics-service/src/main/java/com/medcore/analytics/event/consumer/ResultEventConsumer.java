package com.medcore.analytics.event.consumer;

import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.service.EpidemiologicalService;
import com.medcore.analytics.service.OutbreakDetectionService;
import com.medcore.commons.event.ResultEvent;
import com.medcore.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Escucha {@code result.registered} y {@code result.critical}.
 *
 * <p>Es la unica entrada de datos del servicio: analytics no consulta a
 * laboratory-service ni comparte su base (MC1). Lo que no llegue por estos
 * topicos no existe para la vigilancia.
 *
 * <p>Los dos topicos se atienden con el MISMO metodo y el mismo grupo. Un
 * valor critico se publica en los dos, y registrar el caso es la misma
 * operacion; la idempotencia por {@code idResultado} impide que se cuente dos
 * veces. Separarlo en dos metodos obligaria a repetir esa comprobacion.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como el metodo atrapa toda
 * RuntimeException, un fallo queda en el log y el offset avanza igualmente,
 * de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class ResultEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultEventConsumer.class);

    private final EpidemiologicalService epidemiologicalService;
    private final OutbreakDetectionService outbreakService;

    public ResultEventConsumer(EpidemiologicalService epidemiologicalService,
                               OutbreakDetectionService outbreakService) {
        this.epidemiologicalService = epidemiologicalService;
        this.outbreakService = outbreakService;
    }

    @KafkaListener(
            topics = {KafkaTopics.RESULT_REGISTERED, KafkaTopics.RESULT_CRITICAL},
            groupId = "analytics-service")
    public void alRegistrarseResultado(ResultEvent evento) {
        try {
            if (evento == null || evento.idResultado() == null) {
                log.warn("Evento de resultado incompleto; se descarta");
                return;
            }

            AnalyticsRecord registro = epidemiologicalService.registrar(evento);

            // La deteccion se dispara solo con los casos anormales, y solo
            // para la zona y la prueba del caso: barrer el almacen entero por
            // cada resultado seria desproporcionado, y un resultado normal no
            // puede crear un brote.
            //
            // Se evalua aqui y no solo en el barrido nocturno porque un brote
            // detectado doce horas despues sirve de poco.
            if (registro.anormal()) {
                outbreakService.evaluar(
                        registro.zona(), registro.codigoPrueba(), evento.requestId());
            }

        } catch (RuntimeException e) {
            log.error("Error procesando el resultado {}: {}",
                    evento == null ? "desconocido" : evento.idResultado(), e.getMessage(), e);
        }
    }
}
