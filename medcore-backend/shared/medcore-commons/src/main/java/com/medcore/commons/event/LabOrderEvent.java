package com.medcore.commons.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado por medical-service en {@code lab-order.created}.
 *
 * <p>Lo consume laboratory-service, que abre la orden y queda a la espera de
 * las muestras, y notification-service, que avisa al paciente de que debe
 * acudir al laboratorio.
 *
 * <p>Lleva las pruebas solicitadas dentro del evento y no solo el id de la
 * orden: asi laboratory-service puede registrarlas sin una llamada de vuelta
 * a medical-service, que lo acoplaria.
 *
 * @param pruebas codigos del catalogo de laboratorio, por ejemplo HEM-001
 */
public record LabOrderEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idOrden,
        String codigoOrden,
        Long idAtencion,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,
        String correoPaciente,
        Long idMedico,
        String nombreMedico,
        String prioridad,
        String observaciones,
        List<String> pruebas
) implements BaseEvent {

    public static LabOrderEvent creada(String requestId, Long idOrden, String codigoOrden,
                                       Long idAtencion, Long idPaciente, String dniPaciente,
                                       String nombrePaciente, String correoPaciente,
                                       Long idMedico, String nombreMedico, String prioridad,
                                       String observaciones, List<String> pruebas) {
        return new LabOrderEvent(
                UUID.randomUUID().toString(),
                "lab-order.created",
                Instant.now(),
                "medical-service",
                requestId,
                idOrden, codigoOrden, idAtencion, idPaciente, dniPaciente, nombrePaciente,
                correoPaciente, idMedico, nombreMedico, prioridad, observaciones,
                List.copyOf(pruebas));
    }

    /** Una orden urgente se atiende antes que el resto de la cola. */
    public boolean esUrgente() {
        return "URGENTE".equals(prioridad);
    }
}
