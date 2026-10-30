package com.medcore.laboratory.domain.enums;

import java.util.Set;

/**
 * Estado de una muestra de laboratorio.
 *
 * <pre>
 *   REGISTRADA  -> EN_PROCESO | RECHAZADA
 *   EN_PROCESO  -> PROCESADA  | RECHAZADA
 *   PROCESADA   -> (final)
 *   RECHAZADA   -> (final)
 * </pre>
 *
 * <p>Una muestra se rechaza cuando llega hemolizada, mal rotulada o con
 * volumen insuficiente. Es un desenlace normal del laboratorio, no un error
 * del sistema, y queda registrado con su motivo.
 */
public enum SampleStatus {

    /** Tomada y rotulada; aun no entra al equipo. */
    REGISTRADA,

    /** En el equipo o en la mesa de trabajo. */
    EN_PROCESO,

    /** Tiene resultados para todas las pruebas solicitadas. */
    PROCESADA,

    /** No apta para procesar; hay que volver a tomarla. */
    RECHAZADA;

    public boolean esFinal() {
        return this == PROCESADA || this == RECHAZADA;
    }

    public Set<SampleStatus> siguientesValidos() {
        return switch (this) {
            case REGISTRADA -> Set.of(EN_PROCESO, RECHAZADA);
            case EN_PROCESO -> Set.of(PROCESADA, RECHAZADA);
            case PROCESADA, RECHAZADA -> Set.of();
        };
    }

    public boolean puedePasarA(SampleStatus destino) {
        return siguientesValidos().contains(destino);
    }

    /** Solo se registran resultados sobre una muestra que esta en el equipo. */
    public boolean admiteResultados() {
        return this == EN_PROCESO;
    }
}
