package com.medcore.appointment.domain.enums;

/**
 * Tipo de cita. Determina cuantos slots consecutivos ocupa.
 */
public enum AppointmentType {

    /** Consulta ordinaria: un slot de 30 minutos. */
    CONSULTATION(1),

    /** Control tras un resultado de laboratorio: un slot. */
    LAB_FOLLOW_UP(1),

    /**
     * Urgencia: ocupa dos slots consecutivos, porque no se puede acotar a
     * media hora y encadenar la siguiente cita produciria retrasos.
     */
    EMERGENCY(2);

    private final int slotsOcupados;

    AppointmentType(int slotsOcupados) {
        this.slotsOcupados = slotsOcupados;
    }

    public int slotsOcupados() {
        return slotsOcupados;
    }
}
