package com.medcore.commons.event;

import java.time.Instant;

/**
 * Contrato comun de todo evento publicado en Kafka.
 *
 * <p>Las implementaciones son records inmutables, para que un consumidor no
 * pueda alterar el evento que recibe.
 */
public interface BaseEvent {

    /** Identificador unico del evento; permite descartar duplicados. */
    String eventId();

    /** Nombre del evento, por ejemplo {@code patient.created}. */
    String eventType();

    /** Instante de publicacion, en UTC. */
    Instant occurredAt();

    /** Servicio que lo publico, por ejemplo {@code patient-service}. */
    String source();

    /**
     * Valor de X-Request-Id de la peticion que origino el evento.
     * Permite seguir una operacion a traves de varios servicios en los logs.
     */
    String requestId();
}
