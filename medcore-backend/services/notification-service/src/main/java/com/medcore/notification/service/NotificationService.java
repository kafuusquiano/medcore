package com.medcore.notification.service;

import com.medcore.notification.domain.dto.response.NotificationResponse;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Creacion y consulta de avisos.
 *
 * <p>Los consumidores solo llaman a {@link #crear}: no deciden canales, ni
 * redactan textos, ni envian. Esa separacion es lo que permite que nueve
 * consumidores no repitan la misma logica nueve veces.
 */
public interface NotificationService {

    /**
     * Crea los avisos de un hecho, uno por canal del tipo.
     *
     * <p>Es idempotente por evento, tipo y canal: Kafka entrega al menos una
     * vez, y sin eso un reenvio mandaria el mismo correo dos veces al
     * paciente.
     *
     * <p>Los textos salen de la plantilla del tipo, rellenando sus marcadores
     * con {@code datos}. Un marcador sin valor se deja visible en lugar de
     * quedar como "null", para que se vea que falta un dato.
     *
     * @param destinatario direccion de correo, o null si el tipo solo va
     *                     in-app
     * @return los avisos creados; vacio si ya existian
     */
    List<Notification> crear(NotificationType tipo, String eventId, String destinatario,
                             Long idPaciente, Long idUsuario, Map<String, String> datos);

    /**
     * Igual que {@link #crear}, pero programando el envio para mas tarde.
     *
     * <p>Lo usa el recordatorio de cita, que se crea al reservar y sale el dia
     * antes.
     */
    List<Notification> programar(NotificationType tipo, String eventId, String destinatario,
                                 Long idPaciente, Long idUsuario, Map<String, String> datos,
                                 Instant cuando);

    NotificationResponse obtenerPorId(Long id);

    /** Bandeja in-app de un paciente. */
    Page<NotificationResponse> bandejaDePaciente(Long idPaciente, Pageable pageable);

    /** Bandeja in-app de un usuario interno. */
    Page<NotificationResponse> bandejaDeUsuario(Long idUsuario, Pageable pageable);

    long sinLeerDePaciente(Long idPaciente);

    long sinLeerDeUsuario(Long idUsuario);

    /**
     * Marca un aviso in-app como leido.
     *
     * <p>Solo in-app: un correo no se puede marcar leido, porque el servicio
     * no sabe si el destinatario lo abrio y fingir que lo sabe daria una cifra
     * falsa.
     */
    NotificationResponse marcarLeido(Long id);

    /** Marca como leidos todos los avisos in-app de un paciente. */
    int marcarTodosLeidosDePaciente(Long idPaciente);

    Page<NotificationResponse> listarPorEstado(NotificationStatus estado, Pageable pageable);

    /**
     * Avisos que agotaron sus intentos y nunca llegaron.
     *
     * <p>Es la consulta que de verdad importa: si el correo de un valor
     * critico no salio, alguien tiene que verlo y llamar por telefono.
     */
    List<NotificationResponse> noEntregados();

    /** Cifras para el panel: cuantos pendientes, enviados y perdidos. */
    Resumen resumen();

    record Resumen(long pendientes, long enviados, long fallidos, long omitidos,
                   long sinEntregar) {
    }
}
