package com.medcore.analytics.event.consumer;

import com.medcore.analytics.domain.document.DiagnosisRecord;
import com.medcore.analytics.service.ClinicalSurveillanceService;
import com.medcore.analytics.service.OutbreakDetectionService;
import com.medcore.commons.event.DiagnosisEvent;
import com.medcore.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume diagnosis.registered, la vigilancia por codigo CIE-10.
 *
 * <p>Es la mitad que faltaba de la vigilancia epidemiologica. Antes de esto
 * analytics solo veia lo que pasaba por el laboratorio, asi que un brote de una
 * enfermedad que se diagnostica por clinica y no por analitica era invisible.
 *
 * <p>Los eventos llegan al finalizar la atencion, no al anadir cada
 * diagnostico: uno de una atencion abierta todavia puede cambiar, y un caso
 * retirado no se puede descontar de un informe que ya salio.
 */
@Component
public class DiagnosisEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(DiagnosisEventConsumer.class);

    private final ClinicalSurveillanceService surveillanceService;
    private final OutbreakDetectionService outbreakService;

    public DiagnosisEventConsumer(ClinicalSurveillanceService surveillanceService,
                                  OutbreakDetectionService outbreakService) {
        this.surveillanceService = surveillanceService;
        this.outbreakService = outbreakService;
    }

    @KafkaListener(topics = KafkaTopics.DIAGNOSIS_REGISTERED, groupId = "analytics-service")
    public void alRegistrarseDiagnostico(DiagnosisEvent evento) {
        try {
            if (evento == null || evento.idDiagnostico() == null
                    || evento.codigoCie10() == null) {
                log.warn("Evento de diagnostico incompleto; se descarta");
                return;
            }

            DiagnosisRecord registro = surveillanceService.registrar(evento);

            // Se evalua aqui y no solo en el barrido nocturno porque un brote
            // detectado doce horas despues sirve de poco. Solo la zona y el
            // codigo del caso: barrer el almacen entero por cada diagnostico
            // seria desproporcionado.
            outbreakService.evaluarDiagnostico(
                    registro.zona(), registro.codigoCie10(), evento.requestId());

        } catch (RuntimeException e) {
            // Se atrapa todo: un mensaje problematico no debe bloquear la
            // particion y dejar sin procesar los siguientes, que pueden ser un
            // diagnostico de notificacion obligatoria.
            log.error("Error procesando el diagnostico {}: {}",
                    evento == null ? "desconocido" : evento.idDiagnostico(),
                    e.getMessage(), e);
        }
    }
}
