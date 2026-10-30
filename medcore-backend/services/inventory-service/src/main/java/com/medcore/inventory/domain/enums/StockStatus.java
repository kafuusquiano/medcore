package com.medcore.inventory.domain.enums;

/**
 * Situacion de un lote.
 *
 * <p>No se guarda en la base: se calcula a partir de la cantidad, del minimo
 * del reactivo y de la fecha de vencimiento. Guardarlo obligaria a recalcular
 * la columna cada medianoche, porque un lote pasa a vencido sin que nadie lo
 * toque, y una fila desactualizada diria que hay material usable cuando ya no
 * lo es.
 */
public enum StockStatus {

    /** Vencido. No se puede usar, aunque queden unidades. */
    VENCIDO,

    /** Sin unidades disponibles. */
    AGOTADO,

    /** Por vencer dentro del plazo de aviso configurado. */
    POR_VENCER,

    /** Por debajo del minimo del reactivo: hay que reponer. */
    BAJO,

    /** Disponible y con cantidad suficiente. */
    DISPONIBLE;

    /**
     * Indica si el lote se puede consumir.
     *
     * <p>BAJO y POR_VENCER si: son avisos para reponer o priorizar su uso, no
     * impedimentos. Un lote por vencer es justamente el que conviene gastar
     * primero.
     */
    public boolean esUsable() {
        return this == DISPONIBLE || this == BAJO || this == POR_VENCER;
    }

    /** Indica si la situacion merece aviso al responsable del almacen. */
    public boolean requiereAtencion() {
        return this != DISPONIBLE;
    }

    /**
     * Texto para mostrar en la interfaz.
     *
     * <p>Se define aqui y no en el frontend para que la pantalla del almacen y
     * el informe impreso digan exactamente lo mismo.
     */
    public String etiqueta() {
        return switch (this) {
            case VENCIDO -> "Vencido";
            case AGOTADO -> "Agotado";
            case POR_VENCER -> "Por vencer";
            case BAJO -> "Bajo minimo";
            case DISPONIBLE -> "Disponible";
        };
    }
}
