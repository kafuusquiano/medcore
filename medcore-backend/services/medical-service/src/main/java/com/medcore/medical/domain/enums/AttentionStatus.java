package com.medcore.medical.domain.enums;

import java.util.Set;

/**
 * Estado de una atencion medica.
 *
 * <pre>
 *   EN_CURSO   -> FINALIZADA | ANULADA
 *   FINALIZADA -> (final)
 *   ANULADA    -> (final)
 * </pre>
 *
 * <p>Una atencion finalizada no se modifica: es un registro clinico. Para
 * corregirla se anula y se registra otra, de modo que quede constancia de la
 * correccion y de quien la hizo.
 */
public enum AttentionStatus {

    /** Abierta; el medico aun esta registrando hallazgos. */
    EN_CURSO,

    /** Cerrada. El contenido clinico queda inmutable. */
    FINALIZADA,

    /** Anulada por error de registro. Se conserva para auditoria. */
    ANULADA;

    public boolean esFinal() {
        return this != EN_CURSO;
    }

    public Set<AttentionStatus> siguientesValidos() {
        return switch (this) {
            case EN_CURSO -> Set.of(FINALIZADA, ANULADA);
            case FINALIZADA, ANULADA -> Set.of();
        };
    }

    public boolean puedePasarA(AttentionStatus destino) {
        return siguientesValidos().contains(destino);
    }

    /** Solo una atencion abierta admite diagnosticos u ordenes nuevas. */
    public boolean admiteCambios() {
        return this == EN_CURSO;
    }
}
