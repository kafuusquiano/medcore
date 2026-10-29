package com.medcore.patient.domain.enums;

/**
 * Estado de la ficha del paciente.
 *
 * <p>Las fichas no se borran: un historial clinico es un registro que debe
 * conservarse. Darla de baja solo la saca de las busquedas operativas.
 */
public enum PatientStatus {

    /** Ficha vigente; aparece en busquedas y admite citas nuevas. */
    ACTIVO,

    /** Dada de baja; se conserva el historial pero no admite citas nuevas. */
    INACTIVO,

    /** Paciente fallecido; la ficha queda cerrada para siempre. */
    FALLECIDO;

    public boolean admiteAtencion() {
        return this == ACTIVO;
    }
}
