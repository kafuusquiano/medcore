package com.medcore.appointment.domain.enums;

import java.util.Set;

/**
 * Estado de una cita.
 *
 * <p>Las transiciones validas se declaran aqui y no en el servicio, para que
 * no haya dos sitios donde consultarlas y se contradigan.
 *
 * <pre>
 *   SCHEDULED -> CONFIRMED | CANCELLED | NO_SHOW
 *   CONFIRMED -> COMPLETED | CANCELLED | NO_SHOW
 *   COMPLETED -> (final)
 *   CANCELLED -> (final)
 *   NO_SHOW   -> (final)
 * </pre>
 */
public enum AppointmentStatus {

    /** Reservada, pendiente de que el paciente confirme. */
    SCHEDULED,

    /** Confirmada por el paciente. */
    CONFIRMED,

    /** El paciente acudio y fue atendido. */
    COMPLETED,

    /** Anulada por el paciente o por la clinica. */
    CANCELLED,

    /** El paciente no acudio. */
    NO_SHOW;

    public boolean esFinal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    /** Estados a los que se puede pasar desde este. */
    public Set<AppointmentStatus> siguientesValidos() {
        return switch (this) {
            case SCHEDULED -> Set.of(CONFIRMED, CANCELLED, NO_SHOW);
            case CONFIRMED -> Set.of(COMPLETED, CANCELLED, NO_SHOW);
            case COMPLETED, CANCELLED, NO_SHOW -> Set.of();
        };
    }

    public boolean puedePasarA(AppointmentStatus destino) {
        return siguientesValidos().contains(destino);
    }

    /** Un slot vuelve a quedar libre solo si la cita se anula. */
    public boolean liberaElSlot() {
        return this == CANCELLED;
    }
}
