package com.medcore.notification.event.consumer;

import com.medcore.commons.event.ResultEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Escucha {@code result.registered} y {@code result.critical}.
 *
 * <p>Son dos metodos y no uno porque los avisos son distintos en destinatario
 * y en contenido, que es la decision mas delicada de este servicio:
 *
 * <ul>
 *   <li>{@code result.registered} avisa al PACIENTE de que su resultado esta
 *       disponible, SIN el valor. Enviar un numero por correo, sin su rango ni
 *       nadie que lo interprete, hace que el paciente se alarme o se tranquilice
 *       por su cuenta, y las dos cosas son malas.</li>
 *   <li>{@code result.critical} avisa al MEDICO, con el valor completo, porque
 *       es quien tiene que actuar hoy. Nunca al paciente: un resultado
 *       alarmante por correo, sin nadie que le diga que hacer, es peor que no
 *       avisar.</li>
 * </ul>
 *
 * <p>Un valor critico llega por los DOS topicos, asi que el paciente recibe su
 * aviso de "resultado disponible" y el medico el suyo de valor critico. Es lo
 * correcto: el resultado si esta disponible.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como los metodos atrapan
 * toda RuntimeException, un fallo queda en el log y el offset avanza
 * igualmente, de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class ResultEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultEventConsumer.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    public ResultEventConsumer(NotificationService notificationService,
                               NotificationProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @KafkaListener(topics = KafkaTopics.RESULT_REGISTERED, groupId = "notification-service")
    public void alRegistrarseResultado(ResultEvent evento) {
        try {
            if (evento == null || evento.idResultado() == null) {
                log.warn("Evento result.registered incompleto; se descarta");
                return;
            }

            Map<String, String> datos = new HashMap<>();
            datos.put("idResultado", String.valueOf(evento.idResultado()));
            datos.put("codigoMuestra", evento.codigoMuestra());
            datos.put("nombrePaciente", evento.nombrePaciente());
            datos.put("nombrePrueba", evento.nombrePrueba());

            // A proposito NO se incluye el valor ni la interpretacion: el
            // aviso dice que el resultado esta listo, y el paciente lo
            // consulta en la aplicacion o con su medico.

            notificationService.crear(
                    NotificationType.RESULTADO_LISTO,
                    evento.eventId(),
                    evento.correoPaciente(),
                    evento.idPaciente(),
                    null,
                    datos);

        } catch (RuntimeException e) {
            log.error("Error procesando result.registered del resultado {}: {}",
                    evento == null ? "desconocido" : evento.idResultado(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.RESULT_CRITICAL, groupId = "notification-service")
    public void alDetectarseValorCritico(ResultEvent evento) {
        try {
            if (evento == null || evento.idResultado() == null) {
                log.warn("Evento result.critical incompleto; se descarta");
                return;
            }

            Map<String, String> datos = new HashMap<>();
            datos.put("idResultado", String.valueOf(evento.idResultado()));
            datos.put("codigoMuestra", evento.codigoMuestra());
            datos.put("nombrePaciente", evento.nombrePaciente());
            datos.put("dniPaciente", evento.dniPaciente());
            datos.put("nombrePrueba", evento.nombrePrueba());
            datos.put("valor", valorDe(evento));
            datos.put("unidad", evento.unidad() == null ? "" : evento.unidad());
            datos.put("rango", evento.interpretacion() == null
                    ? "no disponible" : evento.interpretacion());

            // El identificador del evento se sufija para que el aviso al
            // medico no choque con el del paciente en el indice de
            // idempotencia: result.critical y result.registered del mismo
            // resultado traen el mismo eventId del productor solo si este lo
            // reutiliza, y no conviene depender de que no lo haga.
            notificationService.crear(
                    NotificationType.VALOR_CRITICO,
                    evento.eventId() + ":critico",
                    properties.correoInterno(),
                    evento.idPaciente(),
                    evento.idMedico(),
                    datos);

            log.warn("VALOR CRITICO | paciente={} ({}) prueba={} valor={} {} | "
                            + "avisado el medico {} [{}]",
                    evento.nombrePaciente(), evento.dniPaciente(), evento.codigoPrueba(),
                    valorDe(evento), evento.unidad(), evento.idMedico(), evento.requestId());

        } catch (RuntimeException e) {
            // Un fallo aqui es grave: el medico puede quedarse sin aviso. Se
            // registra con ERROR y texto explicito, y el offset avanza para no
            // bloquear los avisos siguientes, que tambien pueden ser criticos.
            log.error("NO SE PUDO AVISAR DEL VALOR CRITICO del resultado {}: {}. "
                            + "Hay que localizar al medico por otro medio.",
                    evento == null ? "desconocido" : evento.idResultado(), e.getMessage(), e);
        }
    }

    /**
     * Valor del resultado como texto.
     *
     * <p>Las pruebas cualitativas no traen numero, y el aviso tiene que decir
     * algo: un correo de valor critico con el campo vacio no sirve para nada.
     */
    private String valorDe(ResultEvent evento) {
        if (evento.valorNumerico() != null) {
            return String.valueOf(evento.valorNumerico());
        }
        return evento.valorTexto() == null ? "sin valor" : evento.valorTexto();
    }
}
