package com.medcore.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Reactivo o insumo del laboratorio.
 *
 * <p>Es el articulo en abstracto, no el material fisico: "Reactivo de
 * hemoglobina, caja de 100 pruebas". Las unidades concretas, con su lote y su
 * fecha de vencimiento, viven en {@link Stock}.
 *
 * <p>Esa separacion existe porque el mismo reactivo llega en varios lotes con
 * vencimientos distintos, y el laboratorio tiene que poder gastar primero el
 * que caduca antes. Con una sola tabla no se sabria que parte del total esta
 * por vencer.
 */
@Entity
@Table(name = "reagents")
public class Reagent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Codigo interno del laboratorio, el que figura en la estanteria.
     * Es unico y lo asigna quien da de alta el reactivo.
     */
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Hematologia, Bioquimica, Microbiologia, Fungible, Equipos. */
    @Column(nullable = false, length = 60)
    private String categoria;

    /** Caja, frasco, vial, unidad. */
    @Column(nullable = false, length = 30)
    private String unidadMedida;

    @Column(length = 120)
    private String proveedor;

    /**
     * Cantidad por debajo de la cual hay que reponer.
     *
     * <p>Se define por reactivo y no global: de un reactivo de uso diario hay
     * que tener mucho mas que de uno que se usa una vez al mes.
     */
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;

    /**
     * Cantidad a la que conviene volver al reponer.
     *
     * <p>No es un maximo que el sistema imponga: es la referencia que se le
     * muestra a quien hace el pedido, para que no compre de menos y vuelva a
     * quedarse corto a la semana.
     */
    @Column(name = "stock_optimo")
    private Integer stockOptimo;

    /** Requiere cadena de frio. Se muestra al recibir y al almacenar. */
    @Column(name = "requiere_refrigeracion", nullable = false)
    private boolean requiereRefrigeracion = false;

    @Column(length = 300)
    private String observaciones;

    /**
     * Un reactivo descatalogado se desactiva, no se borra: sus movimientos
     * historicos deben seguir siendo legibles.
     */
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    private Long version;

    protected Reagent() {
        // Requerido por JPA.
    }

    public Reagent(String codigo, String nombre, String categoria, String unidadMedida,
                   String proveedor, Integer stockMinimo, Integer stockOptimo,
                   boolean requiereRefrigeracion, String observaciones) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidadMedida = unidadMedida;
        this.proveedor = proveedor;
        this.stockMinimo = stockMinimo == null ? 0 : stockMinimo;
        this.stockOptimo = stockOptimo;
        this.requiereRefrigeracion = requiereRefrigeracion;
        this.observaciones = observaciones;
        this.activo = true;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    // ---- Comportamiento de dominio -----------------------------------------

    /**
     * Actualiza los datos administrativos.
     *
     * <p>El codigo no se puede cambiar: esta rotulado en la estanteria y en
     * los movimientos ya registrados.
     */
    public void actualizar(String nombre, String categoria, String unidadMedida,
                           String proveedor, Integer stockMinimo, Integer stockOptimo,
                           boolean requiereRefrigeracion, String observaciones) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidadMedida = unidadMedida;
        this.proveedor = proveedor;
        this.stockMinimo = stockMinimo == null ? 0 : stockMinimo;
        this.stockOptimo = stockOptimo;
        this.requiereRefrigeracion = requiereRefrigeracion;
        this.observaciones = observaciones;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void reactivar() {
        this.activo = true;
    }

    /** Indica si una cantidad total queda por debajo del minimo. */
    public boolean estaBajoMinimo(int cantidadTotal) {
        return cantidadTotal < stockMinimo;
    }

    /**
     * Cuanto conviene pedir para llegar al stock optimo.
     * Devuelve 0 si no hay optimo definido o ya se alcanzo.
     */
    public int cantidadSugeridaDePedido(int cantidadTotal) {
        if (stockOptimo == null || cantidadTotal >= stockOptimo) {
            return 0;
        }
        return stockOptimo - cantidadTotal;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public String getProveedor() {
        return proveedor;
    }

    public Integer getStockMinimo() {
        return stockMinimo;
    }

    public Integer getStockOptimo() {
        return stockOptimo;
    }

    public boolean isRequiereRefrigeracion() {
        return requiereRefrigeracion;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reagent otro)) {
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
        return "Reagent{" + codigo + " - " + nombre + "}";
    }
}
