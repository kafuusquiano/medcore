package com.medcore.inventory.domain.enums;

/**
 * Tipo de movimiento de stock.
 *
 * <p>Cada uno dice si suma o resta, de modo que el servicio no tenga que
 * decidirlo con una cadena de condiciones por cada tipo nuevo.
 *
 * <p>Los movimientos no se borran ni se corrigen: un error se arregla con un
 * AJUSTE que deja constancia de la diferencia. Es un inventario de material
 * sanitario y tiene que poder auditarse.
 */
public enum MovementType {

    /** Compra o donacion que entra al almacen. */
    ENTRADA(true),

    /** Consumo normal al procesar muestras. */
    SALIDA(false),

    /** Lote vencido o deteriorado que se retira. */
    MERMA(false),

    /**
     * Correccion tras un recuento fisico.
     *
     * <p>Puede sumar o restar, asi que su signo lo decide la cantidad y no el
     * tipo. Es el unico caso en que se admite una cantidad negativa.
     */
    AJUSTE(null),

    /** Devolucion al proveedor. */
    DEVOLUCION(false);

    private final Boolean suma;

    MovementType(Boolean suma) {
        this.suma = suma;
    }

    /** Indica si el tipo siempre suma al stock. */
    public boolean esEntrada() {
        return Boolean.TRUE.equals(suma);
    }

    /** Indica si el tipo siempre resta del stock. */
    public boolean esSalida() {
        return Boolean.FALSE.equals(suma);
    }

    /**
     * Un ajuste admite cantidad negativa porque corrige en cualquier
     * direccion; el resto exige cantidad positiva y el signo lo pone el tipo.
     */
    public boolean admiteCantidadNegativa() {
        return this == AJUSTE;
    }

    /**
     * Convierte la cantidad al delta que se aplica al stock.
     *
     * <p>Centralizar el signo aqui evita que cada sitio que mueve stock tenga
     * que recordar si su tipo suma o resta.
     */
    public int deltaDe(int cantidad) {
        if (this == AJUSTE) {
            return cantidad;
        }
        return esEntrada() ? Math.abs(cantidad) : -Math.abs(cantidad);
    }
}
