package com.medcore.laboratory.event.producer;

import com.medcore.commons.event.SampleEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.laboratory.domain.entity.Sample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica {@code sample.registered}.
 *
 * <p>Lo consume notification-service: cuando la muestra se acepta avisa al
 * paciente de cuando esperar el resultado, y cuando se rechaza avisa al
 * medico de que hay que repetir la toma.
 *
 * <p>El rechazo va al mismo topico con otro {@code eventType}, en lugar de a
 * un topico propio. Son dos desenlaces del mismo hecho, la recepcion del
 * tubo, y el mismo consumidor atiende los dos; separarlos obligaria a
 * coordinar el orden de dos particiones distintas.
 *
 * <p>La clave de particion es el identificador del paciente, para que los
 * eventos de un mismo paciente lleguen en orden.
 */
@Component
public class SampleEventProducer {

    private static final Logger log = LoggerFactory.getLogger(SampleEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SampleEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarRegistrada(Sample muestra, String requestId) {
        SampleEvent evento = SampleEvent.registrada(
                requestId,
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
                muestra.getTipoMuestra(),
                muestra.getEstado().name(),
                muestra.isUrgente(),
                muestra.getFechaToma());

        enviar(evento, muestra, "sample.registered");
    }

    public void publicarRechazada(Sample muestra, String motivo, String requestId) {
        SampleEvent evento = SampleEvent.rechazada(
                requestId,
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
                muestra.getTipoMuestra(),
                muestra.isUrgente(),
                motivo,
                muestra.getFechaToma());

        enviar(evento, muestra, "sample.rejected");
    }

    /**
     * Envia el evento sin esperar la confirmacion.
     *
     * <p>Un fallo se registra en el log pero no se propaga: la muestra ya
     * esta guardada y confirmada en base, y lanzar una excepcion aqui no la
     * desharia, solo haria fallar una peticion que en realidad funciono.
     */
    private void enviar(SampleEvent evento, Sample muestra, String tipo) {
        kafkaTemplate.send(KafkaTopics.SAMPLE_REGISTERED,
                        String.valueOf(muestra.getIdPaciente()), evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar {} de la muestra {}: {}",
                                tipo, muestra.getCodigo(), error.getMessage());
                    } else {
                        log.debug("Publicado {} de la muestra {} en la particion {}",
                                tipo, muestra.getCodigo(),
                                resultado.getRecordMetadata().partition());
                    }
                });
    }
}
