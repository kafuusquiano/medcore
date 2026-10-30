package com.medcore.analytics.domain.enums;

/**
 * Estado de una alerta de brote.
 *
 * <pre>
 *   ABIERTA -> CONFIRMADA | DESCARTADA
 * </pre>
 *
 * <p>Una alerta no se borra: se cierra con uno de los dos desenlaces. Que se
 * sepa cuantas se descartaron es lo que permite afinar el umbral despues, y
 * borrarlas dejaria el sistema sin forma de saber si avisa de mas.
 */
public enum AlertStatus {

    /** Detectada y pendiente de revisar. */
    ABIERTA,

    /** Revisada: habia brote. */
    CONFIRMADA,

    /** Revisada: falsa alarma, por ejemplo un cribado programado en la zona. */
    DESCARTADA;

    public boolean esFinal() {
        return this != ABIERTA;
    }

    public boolean puedePasarA(AlertStatus destino) {
        return this == ABIERTA && destino.esFinal();
    }

    public String etiqueta() {
        return switch (this) {
            case ABIERTA -> "Abierta";
            case CONFIRMADA -> "Confirmada";
            case DESCARTADA -> "Descartada";
        };
    }
}
