package com.medcore.laboratory.domain.enums;

/**
 * Interpretacion de un valor frente a sus rangos.
 *
 * <p>Las abreviaturas corresponden al campo OBX-8 (Abnormal Flags) de HL7,
 * para que un resultado emitido por MedCore pueda devolverse a un equipo o a
 * otro sistema sin traducir.
 */
public enum ResultInterpretation {

    /** Dentro del rango de referencia. HL7: N */
    NORMAL("N"),

    /** Por debajo del rango de referencia. HL7: L */
    BAJO("L"),

    /** Por encima del rango de referencia. HL7: H */
    ALTO("H"),

    /** Por debajo del limite critico; exige aviso inmediato. HL7: LL */
    CRITICO_BAJO("LL"),

    /** Por encima del limite critico; exige aviso inmediato. HL7: HH */
    CRITICO_ALTO("HH"),

    /** Prueba cualitativa o sin rango definido. */
    NO_APLICA("");

    private final String codigoHl7;

    ResultInterpretation(String codigoHl7) {
        this.codigoHl7 = codigoHl7;
    }

    public String codigoHl7() {
        return codigoHl7;
    }

    /**
     * Un valor critico dispara result.critical y el aviso inmediato al
     * medico que solicito la prueba.
     */
    public boolean esCritico() {
        return this == CRITICO_BAJO || this == CRITICO_ALTO;
    }

    public boolean esAnormal() {
        return this != NORMAL && this != NO_APLICA;
    }

    /**
     * Texto para mostrar en el informe y en la interfaz.
     *
     * <p>Se define aqui y no en el frontend para que el PDF del laboratorio y
     * la pantalla digan exactamente lo mismo.
     */
    public String etiqueta() {
        return switch (this) {
            case NORMAL -> "Normal";
            case BAJO -> "Bajo";
            case ALTO -> "Alto";
            case CRITICO_BAJO -> "Critico bajo";
            case CRITICO_ALTO -> "Critico alto";
            case NO_APLICA -> "Sin rango de referencia";
        };
    }

    /** Traduce el flag de HL7 que envia un equipo. */
    public static ResultInterpretation desdeHl7(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return NO_APLICA;
        }
        return switch (codigo.trim().toUpperCase()) {
            case "N" -> NORMAL;
            case "L" -> BAJO;
            case "H" -> ALTO;
            case "LL", "<" -> CRITICO_BAJO;
            case "HH", ">" -> CRITICO_ALTO;
            default -> NO_APLICA;
        };
    }
}
