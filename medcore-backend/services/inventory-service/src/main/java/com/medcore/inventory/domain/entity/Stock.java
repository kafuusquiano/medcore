package com.medcore.inventory.domain.entity;

import com.medcore.inventory.domain.enums.StockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Un lote concreto de un reactivo, con su fecha de vencimiento.
 *
 * <p>El stock se lleva por lote y no por reactivo porque es lo que un
 * laboratorio necesita: el mismo reactivo llega en varias remesas con
 * vencimientos distintos, hay que gastar primero el que caduca antes, y
 * cuando un lote sale defectuoso el proveedor lo reclama por su numero de
 * lote, no por el total del almacen.
 *
 * <p>La cantidad solo se modifica registrando un movimiento (regla MC7 del
 * servicio): asi el stock actual y el historial nunca pueden discrepar.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reagent_id", nullable = false)
    private Reagent reactivo;

    /** Numero de lote impreso por el fabricante. */
    @Column(nullable = false, length = 60)
    private String lote;

    @Column(name = "cantidad_actual", nullable = false)
    private Integer cantidadActual = 0;

    /**
     * Cantidad con la que entro el lote.
     *
     * <p>Se conserva para saber cuanto se ha consumido sin tener que sumar
     * todos los movimientos, y para detectar un lote que se gasto demasiado
     * rapido.
     */
    @Column(name = "cantidad_inicial", nullable = false)
    private Integer cantidadInicial = 0;

    /**
     * Nulo en los insumos que no caducan, como gradillas o puntas.
     * Si es nulo, el lote nunca pasa a VENCIDO ni a POR_VENCER.
     */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    /** Estanteria, nevera, congelador. */
    @Column(length = 80)
    private String ubicacion;

    @Column(length = 300)
    private String observaciones;

    /**
     * Pasa a true cuando el lote se retira del almacen, por vencimiento o por
     * deterioro. No se borra la fila: su historial de movimientos tiene que
     * seguir siendo legible.
     */
    @Column(nullable = false)
    private boolean retirado = false;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /**
     * Bloqueo optimista. Dos tecnicos pueden consumir del mismo lote a la vez;
     * el segundo recibe un conflicto en lugar de sobrescribir la cantidad que
     * dejo el primero.
     */
    @Version
    private Long version;

    protected Stock() {
        // Requerido por JPA.
    }

    public Stock(Reagent reactivo, String lote, Integer cantidadInicial,
                 LocalDate fechaVencimiento, LocalDate fechaIngreso,
                 String ubicacion, String observaciones) {
        this.reactivo = reactivo;
        this.lote = lote;
        this.cantidadInicial = cantidadInicial == null ? 0 : cantidadInicial;
        this.cantidadActual = this.cantidadInicial;
        this.fechaVencimiento = fechaVencimiento;
        this.fechaIngreso = fechaIngreso == null ? LocalDate.now() : fechaIngreso;
        this.ubicacion = ubicacion;
        this.observaciones = observaciones;
    }

    @PrePersist
    @PreUpdate
    void alGuardar() {
        this.fechaActualizacion = Instant.now();
    }

    // ---- Comportamiento de dominio -----------------------------------------

    /**
     * Aplica un movimiento a la cantidad del lote.
     *
     * <p>El delta ya viene con su signo, calculado por MovementType. Se
     * rechaza si dejaria el lote en negativo: no se puede consumir material
     * que no existe, y permitirlo haria que el inventario dejara de
     * corresponder con la estanteria.
     *
     * @throws IllegalArgumentException si el resultado seria negativo
     */
    public void aplicarMovimiento(int delta) {
        int resultado = cantidadActual + delta;
        if (resultado < 0) {
            throw new IllegalArgumentException(
                    "El lote " + lote + " solo tiene " + cantidadActual
                            + " unidades y se intentan descontar " + Math.abs(delta) + ".");
        }
        this.cantidadActual = resultado;
    }

    /**
     * Retira el lote del almacen.
     *
     * <p>No pone la cantidad a cero: para eso se registra una MERMA, que es
     * la que deja constancia de cuantas unidades se perdieron. Separar ambas
     * cosas permite saber si un lote se retiro lleno o ya casi gastado.
     */
    public void retirar(String motivo) {
        this.retirado = true;
        this.observaciones = (observaciones == null ? "" : observaciones + " | ")
                + "Retirado: " + motivo;
    }

    public void reubicar(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    /**
     * Situacion del lote, calculada en el momento.
     *
     * <p>El orden de comprobacion importa: un lote vencido lo esta aunque
     * tenga unidades, y uno agotado no interesa que figure como bajo minimo.
     *
     * @param diasAvisoVencimiento antelacion con la que se avisa, de la
     *                             configuracion del servicio
     */
    public StockStatus situacion(int diasAvisoVencimiento) {
        if (estaVencido()) {
            return StockStatus.VENCIDO;
        }
        if (cantidadActual <= 0) {
            return StockStatus.AGOTADO;
        }
        if (venceEnMenosDe(diasAvisoVencimiento)) {
            return StockStatus.POR_VENCER;
        }
        if (reactivo.estaBajoMinimo(cantidadActual)) {
            return StockStatus.BAJO;
        }
        return StockStatus.DISPONIBLE;
    }

    public boolean estaVencido() {
        return fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now());
    }

    public boolean venceEnMenosDe(int dias) {
        if (fechaVencimiento == null || estaVencido()) {
            return false;
        }
        return diasHastaVencimiento() <= dias;
    }

    /** Dias que faltan para vencer; nulo si el lote no caduca. */
    public Long diasHastaVencimiento() {
        if (fechaVencimiento == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }

    /**
     * Indica si el lote se puede consumir.
     *
     * <p>Un lote retirado o vencido no, aunque queden unidades: usarlo daria
     * un resultado clinico que no se puede respaldar.
     */
    public boolean esConsumible() {
        return !retirado && !estaVencido() && cantidadActual > 0;
    }

    public int unidadesConsumidas() {
        return Math.max(0, cantidadInicial - cantidadActual);
    }

    public Long getId() {
        return id;
    }

    public Reagent getReactivo() {
        return reactivo;
    }

    public String getLote() {
        return lote;
    }

    public Integer getCantidadActual() {
        return cantidadActual;
    }

    public Integer getCantidadInicial() {
        return cantidadInicial;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public boolean isRetirado() {
        return retirado;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Stock otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Stock{lote=" + lote + ", cantidad=" + cantidadActual + "}";
    }
}
