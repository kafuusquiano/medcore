package com.medcore.medical.domain.enums;

import java.util.Set;

/**
 * Estado de una orden de laboratorio desde la perspectiva de medical-service.
 *
 * <p>El avance real lo marca laboratory-service: aqui se refleja lo que aquel
 * comunica, mas la anulacion, que si decide el medico que la emitio.
 *
 * <pre>
 *   EMITIDA    -> EN_PROCESO | ANULADA
 *   EN_PROCESO -> COMPLETADA
 *   COMPLETADA -> (final)
 *   ANULADA    -> (final)
 * </pre>
 */
public enum LabOrderStatus {

    /** Creada por el medico; laboratory-service aun no tomo la muestra. */
    EMITIDA,

    /** El laboratorio registro la muestra y esta procesando. */
    EN_PROCESO,

    /** Hay resultados para todas las pruebas. */
    COMPLETADA,

    /** Anulada antes de tomar la muestra. */
    ANULADA;

    public boolean esFinal() {
        return this == COMPLETADA || this == ANULADA;
    }

    public Set<LabOrderStatus> siguientesValidos() {
        return switch (this) {
            case EMITIDA -> Set.of(EN_PROCESO, ANULADA);
            case EN_PROCESO -> Set.of(COMPLETADA);
            case COMPLETADA, ANULADA -> Set.of();
        };
    }

    public boolean puedePasarA(LabOrderStatus destino) {
        return siguientesValidos().contains(destino);
    }

    /**
     * Una orden solo se anula antes de que el laboratorio empiece: despues
     * ya hay una muestra tomada al paciente.
     */
    public boolean admiteAnulacion() {
        return this == EMITIDA;
    }
}
