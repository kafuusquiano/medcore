package com.medcore.medical.domain.enums;

/**
 * Prioridad de una orden de laboratorio. Determina el orden de la cola en
 * laboratory-service y si notification-service avisa de inmediato.
 */
public enum LabOrderPriority {

    /** Flujo normal: se procesa por orden de llegada. */
    RUTINA,

    /** Se adelanta en la cola del laboratorio. */
    URGENTE;

    public boolean esUrgente() {
        return this == URGENTE;
    }
}
