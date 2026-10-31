package com.medcore.medical.event.producer;

import com.medcore.commons.event.DiagnosisEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.medical.domain.entity.Diagnosis;
import com.medcore.medical.domain.entity.MedicalAttention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica diagnosis.registered.
 *
 * <p>Lo consume analytics-service, para vigilar por codigo CIE-10 y no solo por
 * prueba de laboratorio anormal. Sin esto, un brote de una enfermedad que se
 * diagnostica por clinica y no por analitica era invisible para la vigilancia.
 *
 * <p>Se publica al FINALIZAR la atencion y no al anadir cada diagnostico. Un
 * diagnostico de una atencion abierta todavia puede cambiar: el medico lo
 * quita, cambia el principal o anade otro. Publicarlo antes haria que la
 * vigilancia contara casos que luego se corrigen, y un caso retirado no se
 * puede descontar de un informe que ya salio.
 *
 * <p>La clave de particion es la zona y no el paciente, al contrario que en los
 * demas topicos: lo que analytics agrupa es el territorio, y asi los casos de
 * un mismo distrito llegan en orden a la misma particion.
 */
@Component
public class DiagnosisEventProducer {

    private static final Logger log =
            LoggerFactory.getLogger(DiagnosisEventProducer.class);

    /** Clave cuando no se conoce la zona, para no publicar con clave nula. */
    private static final String SIN_ZONA = "sin-zona";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DiagnosisEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica un evento por cada diagnostico de la atencion finalizada.
     *
     * <p>Uno por diagnostico y no uno con la lista: analytics cuenta casos por
     * codigo, y un evento con varios codigos le obligaria a desgranarlo y a
     * inventarse una clave de idempotencia por cada parte.
     */
    public void publicarDeAtencionFinalizada(MedicalAttention atencion, String requestId) {
        for (Diagnosis diagnostico : atencion.getDiagnosticos()) {
            publicar(atencion, diagnostico, requestId);
        }
    }

    private void publicar(MedicalAttention atencion, Diagnosis diagnostico,
                          String requestId) {
        DiagnosisEvent evento = DiagnosisEvent.registrado(
                requestId,
                diagnostico.getId(),
                atencion.getId(),
                atencion.getIdPaciente(),
                atencion.getDniPaciente(),
                atencion.getZonaPaciente(),
                atencion.getEdadPaciente(),
                atencion.getSexoPaciente(),
                atencion.getIdMedico(),
                diagnostico.getCodigoCie10(),
                diagnostico.getDescripcionCie10(),
                diagnostico.isPrincipal(),
                diagnostico.isNotificable(),
                atencion.getFechaAtencion() == null
                        ? null
                        : atencion.getFechaAtencion().atZone(
                                com.medcore.commons.util.DateUtil.ZONA_LIMA)
                                .toLocalDate());

        String clave = atencion.getZonaPaciente() == null
                || atencion.getZonaPaciente().isBlank()
                ? SIN_ZONA
                : atencion.getZonaPaciente();

        kafkaTemplate.send(KafkaTopics.DIAGNOSIS_REGISTERED, clave, evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        // No se reintenta ni se propaga: la atencion ya esta
                        // finalizada y es un hecho clinico. Perder un caso en
                        // la estadistica es malo, pero deshacer el cierre de
                        // una atencion por un fallo de Kafka es peor.
                        log.error("No se pudo publicar diagnosis.registered del "
                                        + "diagnostico {} de la atencion {}: {}",
                                diagnostico.getCodigoCie10(), atencion.getId(),
                                error.getMessage());
                    } else {
                        log.debug("Publicado diagnosis.registered {} de la atencion {} "
                                        + "en la particion {}",
                                diagnostico.getCodigoCie10(), atencion.getId(),
                                resultado.getRecordMetadata().partition());
                    }
                });
    }
}
