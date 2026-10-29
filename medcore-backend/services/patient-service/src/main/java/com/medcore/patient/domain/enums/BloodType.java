package com.medcore.patient.domain.enums;

/**
 * Grupo sanguineo y factor Rh.
 *
 * <p>El nombre del enum no puede llevar los simbolos + y -, asi que se usa
 * POS y NEG, y {@link #etiqueta()} devuelve la forma que se muestra al
 * usuario y se imprime en los documentos.
 */
public enum BloodType {

    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-"),
    /** Aun no determinado; es lo habitual en un registro reciente. */
    DESCONOCIDO("Desconocido");

    private final String etiqueta;

    BloodType(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
