package com.medcore.notification.event.consumer;

import com.medcore.commons.event.AppointmentEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Escucha {@code appointment.created} y {@code appointment.cancelled}.
 *
 * <p>Al reservar hace dos cosas: confirma la cita y PROGRAMA el recordatorio
 * para el dia anterior. El recordatorio se crea aqui y no en un proceso que
 * barra las citas de manana porque asi queda guardado desde el principio, y el
 * proceso periodico solo tiene que recoger lo que ya tiene hora cumplida.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como los metodos atrapan
 * toda RuntimeException, un fallo queda en el log y el offset avanza
 * igualmente, de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Antelacion del recordatorio. Un dia es lo que da margen a reorganizarse. */
    private static final int HORAS_ANTES = 24;

    private final NotificationService notificationService;
    private final ZoneId zonaHoraria = ZoneId.systemDefault();

    public AppointmentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopics.APPOINTMENT_CREATED, groupId = "notification-service")
    public void alReservarseCita(AppointmentEvent evento) {
        try {
            if (evento == null || evento.idCita() == null) {
                log.warn("Evento appointment.created incompleto; se descarta");
                return;
            }

            Map<String, String> datos = datosDe(evento);

            notificationService.crear(
                    NotificationType.CITA_RESERVADA,
                    evento.eventId(),
                    evento.correoPaciente(),
                    evento.idPaciente(),
                    null,
                    datos);

            programarRecordatorio(evento, datos);

        } catch (RuntimeException e) {
            log.error("Error procesando appointment.created de la cita {}: {}",
                    evento == null ? "desconocida" : evento.idCita(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.APPOINTMENT_CANCELLED, groupId = "notification-service")
    public void alCancelarseCita(AppointmentEvent evento) {
        try {
            if (evento == null || evento.idCita() == null) {
                log.warn("Evento appointment.cancelled incompleto; se descarta");
                return;
            }

            Map<String, String> datos = datosDe(evento);
            datos.put("motivo", evento.motivoCancelacion() == null
                    ? "no indicado"
                    : evento.motivoCancelacion());

            // Se avisa siempre, aunque la cancele el propio paciente: la
            // confirmacion es lo que le permite comprobar que la cancelacion
            // se registro, y evita que se presente igualmente.
            notificationService.crear(
                    NotificationType.CITA_CANCELADA,
                    evento.eventId(),
                    evento.correoPaciente(),
                    evento.idPaciente(),
                    null,
                    datos);

        } catch (RuntimeException e) {
            log.error("Error procesando appointment.cancelled de la cita {}: {}",
                    evento == null ? "desconocida" : evento.idCita(), e.getMessage(), e);
        }
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Programa el recordatorio para 24 horas antes de la cita.
     *
     * <p>Si la cita es para dentro de menos de un dia, no se programa: un
     * recordatorio con fecha pasada saldria de inmediato, justo despues de la
     * confirmacion, y el paciente recibiria dos correos seguidos diciendo lo
     * mismo.
     *
     * <p>El recordatorio NO se cancela aqui cuando la cita se anula: el
     * proceso que recoge la cola comprueba el estado antes de enviarlo. Hacerlo
     * de otro modo exigiria que este servicio supiera que citas siguen vivas,
     * que es trabajo de appointment-service.
     */
    private void programarRecordatorio(AppointmentEvent evento, Map<String, String> datos) {
        if (evento.fechaHora() == null) {
            return;
        }

        Instant cuando = evento.fechaHora()
                .minusHours(HORAS_ANTES)
                .atZone(zonaHoraria)
                .toInstant();

        if (cuando.isBefore(Instant.now())) {
            log.debug("La cita {} es dentro de menos de {} horas; no se programa recordatorio",
                    evento.idCita(), HORAS_ANTES);
            return;
        }

        notificationService.programar(
                NotificationType.CITA_RECORDATORIO,
                // El identificador del evento se sufija para que el
                // recordatorio no choque con la confirmacion en el indice de
                // idempotencia: son dos avisos distintos del mismo hecho.
                evento.eventId() + ":recordatorio",
                evento.correoPaciente(),
                evento.idPaciente(),
                null,
                datos,
                cuando);
    }

    private Map<String, String> datosDe(AppointmentEvent evento) {
        Map<String, String> datos = new HashMap<>();
        datos.put("idCita", String.valueOf(evento.idCita()));
        datos.put("nombrePaciente", evento.nombrePaciente());
        datos.put("dniPaciente", evento.dniPaciente());
        datos.put("tipoCita", evento.tipoCita());

        // El evento lleva el identificador del medico, no su nombre. Se
        // escribe asi en lugar de dejar el hueco visible: un correo que diga
        // "su cita con el medico 9" es peor que uno que diga "con su medico".
        datos.put("nombreMedico", "su medico asignado");

        LocalDateTime fechaHora = evento.fechaHora();
        datos.put("fecha", fechaHora == null ? "por confirmar" : fechaHora.format(FECHA));
        datos.put("hora", fechaHora == null ? "por confirmar" : fechaHora.format(HORA));

        return datos;
    }
}
