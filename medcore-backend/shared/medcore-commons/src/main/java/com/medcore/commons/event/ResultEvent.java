package com.medcore.commons.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado por laboratory-service en {@code result.registered} y
 * {@code result.critical}.
 *
 * <p>{@code result.registered} lo consumen analytics-service, que alimenta
 * los indicadores epidemiologicos, y notification-service, que avisa al
 * paciente de que su resultado esta listo.
 *
 * <p>{@code result.critical} se publica ADEMAS del anterior cuando el valor
 * cae fuera del rango critico. Se emite como topico aparte para que
 * notification-service pueda tratarlo con prioridad sin inspeccionar el
 * contenido de cada resultado.
 *
 * @param interpretacion NORMAL, BAJO, ALTO, CRITICO_BAJO o CRITICO_ALTO
 * @param valorNumerico  nulo en las pruebas cualitativas, por ejemplo un cultivo
 */
public record ResultEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idResultado,
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
        String codigoPrueba,
        String nombrePrueba,
        Double valorNumerico,
        String valorTexto,
        String unidad,
        String interpretacion,
        boolean critico,
        Instant fechaResultado
) implements BaseEvent {

    public static ResultEvent de(String tipoEvento, String requestId, Long idResultado,
                                 Long idMuestra, String codigoMuestra, Long idOrden,
                                 String codigoOrden, Long idPaciente, String dniPaciente,
                                 String nombrePaciente, String correoPaciente, String zonaPaciente,
                                 Long idMedico, String codigoPrueba, String nombrePrueba,
                                 Double valorNumerico, String valorTexto, String unidad,
                                 String interpretacion, boolean critico, Instant fechaResultado) {
        return new ResultEvent(
                UUID.randomUUID().toString(),
                tipoEvento,
                Instant.now(),
                "laboratory-service",
                requestId,
                idResultado, idMuestra, codigoMuestra, idOrden, codigoOrden, idPaciente,
                dniPaciente, nombrePaciente, correoPaciente, zonaPaciente, idMedico,
                codigoPrueba, nombrePrueba, valorNumerico, valorTexto, unidad,
                interpretacion, critico, fechaResultado);
    }
}
