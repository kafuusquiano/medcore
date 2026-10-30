package com.medcore.commons.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento del topico {@code sample.registered}.
 *
 * <p>Lo publica laboratory-service cuando recibe el tubo, y tambien cuando lo
 * rechaza: en los dos casos hay algo que comunicar. Si la muestra se acepta,
 * notification-service avisa al paciente de cuando esperar el resultado. Si
 * se rechaza, el aviso va al medico, porque hay que repetir la toma.
 *
 * <p>Se distingue el caso por {@code eventType}, no por un campo booleano:
 * asi un consumidor puede filtrar por tipo sin deserializar el cuerpo.
 *
 * @param codigoMuestra  el de la etiqueta del tubo, por ejemplo
 *                       MUE-20261029-0012; es el que el paciente ve en su
 *                       comprobante
 * @param motivoRechazo  solo en {@code sample.rejected}
 * @param zonaPaciente   la usa analytics-service para agrupar por distrito
 */
public record SampleEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idMuestra,
        String codigoMuestra,
        Long idOrden,
        String codigoOrden,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,
        String correoPaciente,
        String zonaPaciente,
        Long idMedico,
        String tipoMuestra,
        String estado,
        boolean urgente,
        String motivoRechazo,
        Instant fechaToma
) implements BaseEvent {

    public static SampleEvent registrada(String requestId, Long idMuestra, String codigoMuestra,
                                         Long idOrden, String codigoOrden, Long idPaciente,
                                         String dniPaciente, String nombrePaciente,
                                         String correoPaciente, String zonaPaciente,
                                         Long idMedico, String tipoMuestra, String estado,
                                         boolean urgente, Instant fechaToma) {
        return new SampleEvent(
                UUID.randomUUID().toString(),
                "sample.registered",
                Instant.now(),
                "laboratory-service",
                requestId,
                idMuestra, codigoMuestra, idOrden, codigoOrden, idPaciente, dniPaciente,
                nombrePaciente, correoPaciente, zonaPaciente, idMedico, tipoMuestra,
                estado, urgente, null, fechaToma);
    }

    public static SampleEvent rechazada(String requestId, Long idMuestra, String codigoMuestra,
                                        Long idOrden, String codigoOrden, Long idPaciente,
                                        String dniPaciente, String nombrePaciente,
                                        String correoPaciente, String zonaPaciente,
                                        Long idMedico, String tipoMuestra, boolean urgente,
                                        String motivoRechazo, Instant fechaToma) {
        return new SampleEvent(
                UUID.randomUUID().toString(),
                "sample.rejected",
                Instant.now(),
                "laboratory-service",
                requestId,
                idMuestra, codigoMuestra, idOrden, codigoOrden, idPaciente, dniPaciente,
                nombrePaciente, correoPaciente, zonaPaciente, idMedico, tipoMuestra,
                "RECHAZADA", urgente, motivoRechazo, fechaToma);
    }

    public boolean esRechazo() {
        return "sample.rejected".equals(eventType);
    }
}
