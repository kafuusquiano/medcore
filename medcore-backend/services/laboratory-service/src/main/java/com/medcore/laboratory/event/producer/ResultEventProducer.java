package com.medcore.laboratory.event.producer;

import com.medcore.commons.event.ResultEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica {@code result.registered} y {@code result.critical}.
 *
 * <p>Son dos topicos y no uno con una marca porque sus consumidores difieren:
 * {@code result.registered} lo lee analytics-service para sus series y
 * notification-service para avisar al paciente de que su resultado esta
 * listo; {@code result.critical} desencadena el aviso inmediato al medico
 * tratante, y debe poder tener su propia configuracion de reintentos y su
 * propio grupo de consumo sin arrastrar el volumen de todos los resultados.
 *
 * <p>Un valor critico se publica en los dos: tambien forma parte del
 * historial y de las estadisticas.
 */
@Component
public class ResultEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ResultEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ResultEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarRegistrado(Result resultado, Sample muestra, String requestId) {
        enviar(KafkaTopics.RESULT_REGISTERED,
                construir("result.registered", resultado, muestra, requestId),
                resultado, muestra, "result.registered");
    }

    public void publicarCritico(Result resultado, Sample muestra, String requestId) {
        enviar(KafkaTopics.RESULT_CRITICAL,
                construir("result.critical", resultado, muestra, requestId),
                resultado, muestra, "result.critical");
    }

    private ResultEvent construir(String tipo, Result resultado, Sample muestra, String requestId) {
        return ResultEvent.de(
                tipo,
                requestId,
                resultado.getId(),
                muestra.getId(),
                muestra.getCodigo(),
                muestra.getIdOrden(),
                muestra.getCodigoOrden(),
                muestra.getIdPaciente(),
                muestra.getDniPaciente(),
                muestra.getNombrePaciente(),
                muestra.getCorreoPaciente(),
                muestra.getZonaPaciente(),
                muestra.getIdMedico(),
                resultado.getCodigoPrueba(),
                resultado.getNombrePrueba(),
                resultado.getValorNumerico(),
                resultado.getValorTexto(),
                resultado.getUnidad(),
                resultado.getInterpretacion().name(),
                resultado.isCritico(),
                resultado.getFechaResultado());
    }

    /**
     * Envia el evento sin esperar la confirmacion.
     *
     * <p>Un fallo se registra pero no se propaga: el resultado ya esta
     * confirmado en base y lanzar aqui no lo desharia.
     *
     * <p>Para un valor critico el fallo se registra con nivel ERROR y texto
     * explicito: significa que el aviso automatico al medico no va a salir, y
     * el laboratorio debe localizarlo por telefono. El registro local del
     * valor critico lo deja ademas CriticalValueService.
     */
    private void enviar(String topico, ResultEvent evento, Result resultado,
                        Sample muestra, String tipo) {
        kafkaTemplate.send(topico, String.valueOf(muestra.getIdPaciente()), evento)
                .whenComplete((envio, error) -> {
                    if (error == null) {
                        log.debug("Publicado {} del resultado {} en la particion {}",
                                tipo, resultado.getId(),
                                envio.getRecordMetadata().partition());
                        return;
                    }

                    if (resultado.isCritico()) {
                        log.error("NO SE PUDO PUBLICAR {} del valor critico de {} en la "
                                        + "muestra {}: {}. El aviso automatico al medico no "
                                        + "saldra; hay que localizarlo manualmente.",
                                tipo, resultado.getCodigoPrueba(), muestra.getCodigo(),
                                error.getMessage());
                    } else {
                        log.error("No se pudo publicar {} del resultado {}: {}",
                                tipo, resultado.getId(), error.getMessage());
                    }
                });
    }
}
