package com.medcore.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Reactivo que gasta una prueba del catalogo de laboratorio.
 *
 * <p>Es lo que permite que el almacen se descuente solo. Sin esta tabla el
 * consumo habia que apuntarlo a mano, asi que el stock del sistema se separaba
 * del real en cuanto alguien se olvidaba, y la alerta de reposicion llegaba
 * tarde o no llegaba.
 *
 * <p>Una prueba puede tener varias lineas: el recuento leucocitario gasta
 * diluyente y lisante, y un urocultivo gasta dos placas y una tira de
 * identificacion.
 */
@Entity
@Table(name = "test_reagent_usage")
public class TestReagentUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Codigo del catalogo de laboratorio, como HEM-001.
     *
     * <p>No es una clave ajena: el catalogo vive en lab_db, y las bases de dos
     * servicios no se cruzan. El precio es que un codigo que desaparezca del
     * catalogo deja aqui una linea que ya no se usa; a cambio, cada servicio
     * puede migrar su base sin coordinarse con el otro.
     */
    @Column(name = "codigo_prueba", nullable = false, length = 20)
    private String codigoPrueba;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reagent_id", nullable = false)
    private Reagent reactivo;

    /**
     * Unidades que gasta UNA determinacion.
     *
     * <p>Casi siempre 1, pero una tecnica puede llevar dos unidades del mismo
     * reactivo.
     */
    @Column(name = "cantidad_por_prueba", nullable = false)
    private Integer cantidadPorPrueba;

    /**
     * Una linea se desactiva en lugar de borrarse.
     *
     * <p>Los movimientos ya registrados la referencian como motivo del
     * descuento, y borrarla dejaria un consumo sin explicacion.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "observaciones", length = 300)
    private String observaciones;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected TestReagentUsage() {
    }

    public TestReagentUsage(String codigoPrueba, Reagent reactivo,
                            Integer cantidadPorPrueba, String observaciones) {
        this.codigoPrueba = codigoPrueba;
        this.reactivo = reactivo;
        this.cantidadPorPrueba = cantidadPorPrueba;
        this.observaciones = observaciones;
        this.activo = true;
    }

    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = Instant.now();
        }
    }

    /** Lo que gasta un numero de determinaciones de esta prueba. */
    public int consumoDe(int determinaciones) {
        if (determinaciones <= 0) {
            return 0;
        }
        return cantidadPorPrueba * determinaciones;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getCodigoPrueba() {
        return codigoPrueba;
    }

    public Reagent getReactivo() {
        return reactivo;
    }

    public Integer getCantidadPorPrueba() {
        return cantidadPorPrueba;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }
}
