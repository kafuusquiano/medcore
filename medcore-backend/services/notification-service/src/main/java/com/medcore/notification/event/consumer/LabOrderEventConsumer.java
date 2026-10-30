package com.medcore.notification.event.consumer;

import com.medcore.commons.event.LabOrderEvent;
import com.medcore.commons.event.SampleEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escucha {@code lab-order.created} y {@code sample.registered}.
 *
 * <p>Los dos pertenecen al mismo recorrido del paciente por el laboratorio, de
 * la orden a la muestra, y comparten el formato de sus datos; separarlos en dos
 * clases obligaria a repetir el armado.
 *
 * <p>El topico sample.registered lleva dos desenlaces distinguidos por
 * eventType: la muestra aceptada avisa al PACIENTE, y la rechazada al MEDICO.
 * Esa diferencia importa: quien decide si hay que repetir la toma es el
 * medico, y el paciente recibiria un aviso que no sabe que hacer con el.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como los metodos atrapan
 * toda RuntimeException, un fallo queda en el log y el offset avanza
 * igualmente, de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class LabOrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LabOrderEventConsumer.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    public LabOrderEventConsumer(NotificationService notificationService,
                                 NotificationProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @KafkaListener(topics = KafkaTopics.LAB_ORDER_CREATED, groupId = "notification-service")
    public void alEmitirseOrden(LabOrderEvent evento) {
        try {
            if (evento == null || evento.idOrden() == null) {
                log.warn("Evento lab-order.created incompleto; se descarta");
                return;
            }

            Map<String, String> datos = new HashMap<>();
            datos.put("idOrden", String.valueOf(evento.idOrden()));
            datos.put("codigoOrden", evento.codigoOrden());
            datos.put("nombrePaciente", evento.nombrePaciente());
            datos.put("nombreMedico", evento.nombreMedico() == null
                    ? "su medico" : evento.nombreMedico());
            datos.put("pruebas", listarPruebas(evento.pruebas()));

            // Las indicaciones son lo que el paciente necesita saber ANTES de
            // acudir: un ayuno incumplido obliga a repetir la toma. Si la
            // orden no las trae, se dice explicitamente en lugar de dejar el
            // hueco.
            datos.put("indicaciones", evento.observaciones() == null
                            || evento.observaciones().isBlank()
                    ? "sin indicaciones especiales"
                    : evento.observaciones());

            notificationService.crear(
                    NotificationType.ORDEN_LABORATORIO,
                    evento.eventId(),
                    evento.correoPaciente(),
                    evento.idPaciente(),
                    null,
                    datos);

        } catch (RuntimeException e) {
            log.error("Error procesando lab-order.created de la orden {}: {}",
                    evento == null ? "desconocida" : evento.codigoOrden(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.SAMPLE_REGISTERED, groupId = "notification-service")
    public void alRegistrarseMuestra(SampleEvent evento) {
        try {
            if (evento == null || evento.idMuestra() == null) {
                log.warn("Evento de muestra incompleto; se descarta");
                return;
            }

            Map<String, String> datos = new HashMap<>();
            datos.put("idMuestra", String.valueOf(evento.idMuestra()));
            datos.put("codigoMuestra", evento.codigoMuestra());
            datos.put("codigoOrden", evento.codigoOrden());
            datos.put("nombrePaciente", evento.nombrePaciente());
            datos.put("dniPaciente", evento.dniPaciente());
            datos.put("tipoMuestra", evento.tipoMuestra());

            if (evento.esRechazo()) {
                datos.put("motivo", evento.motivoRechazo() == null
                        ? "no indicado" : evento.motivoRechazo());

                // Al MEDICO, no al paciente. El correo va al buzon interno
                // porque el evento solo trae el identificador del medico, no
                // su direccion; el aviso in-app si llega a su bandeja.
                notificationService.crear(
                        NotificationType.MUESTRA_RECHAZADA,
                        evento.eventId(),
                        properties.correoInterno(),
                        null,
                        evento.idMedico(),
                        datos);

                log.info("Muestra {} rechazada: avisado el medico {} [{}]",
                        evento.codigoMuestra(), evento.idMedico(), evento.requestId());
                return;
            }

            notificationService.crear(
                    NotificationType.MUESTRA_REGISTRADA,
                    evento.eventId(),
                    evento.correoPaciente(),
                    evento.idPaciente(),
                    null,
                    datos);

        } catch (RuntimeException e) {
            log.error("Error procesando el evento de la muestra {}: {}",
                    evento == null ? "desconocida" : evento.codigoMuestra(), e.getMessage(), e);
        }
    }

    /**
     * Lista las pruebas en un texto legible.
     *
     * <p>Se recortan a cinco: un correo con treinta codigos es ilegible, y el
     * detalle completo esta en la orden que el enlace abre.
     */
    private String listarPruebas(List<String> pruebas) {
        if (pruebas == null || pruebas.isEmpty()) {
            return "las indicadas por su medico";
        }
        if (pruebas.size() <= 5) {
            return String.join(", ", pruebas);
        }
        return String.join(", ", pruebas.subList(0, 5))
                + " y " + (pruebas.size() - 5) + " mas";
    }
}
