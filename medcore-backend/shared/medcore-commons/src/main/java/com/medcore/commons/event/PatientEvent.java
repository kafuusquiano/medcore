package com.medcore.commons.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento publicado por patient-service en {@code patient.created} y
 * {@code patient.updated}.
 *
 * <p>Lo consumen auth-service, que crea el usuario con rol PACIENTE, y
 * appointment-service, que cachea los datos minimos del paciente para no
 * llamar a patient-service en cada consulta de agenda.
 *
 * <p>Lleva solo lo que los consumidores necesitan, no la ficha completa: un
 * evento no es un volcado de la entidad.
 */
public record PatientEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idPaciente,
        String dni,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String correo,
        String telefono
) implements BaseEvent {

    public static PatientEvent creado(String tipoEvento, String requestId, Long idPaciente,
                                      String dni, String nombres, String apellidos,
                                      LocalDate fechaNacimiento, String correo, String telefono) {
        return new PatientEvent(
                UUID.randomUUID().toString(),
                tipoEvento,
                Instant.now(),
                "patient-service",
                requestId,
                idPaciente, dni, nombres, apellidos, fechaNacimiento, correo, telefono);
    }

    /** Nombre completo, para los mensajes dirigidos al paciente. */
    public String nombreCompleto() {
        return (nombres + " " + apellidos).trim();
    }
}
