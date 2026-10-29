package com.medcore.medical.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Prueba concreta solicitada dentro de una orden de laboratorio.
 *
 * <p>El codigo pertenece al catalogo de laboratory-service, que es el dueno
 * de esa informacion (MC1). Aqui se guarda ademas el nombre con el que se
 * solicito, para que la orden impresa siga siendo legible aunque el catalogo
 * cambie la redaccion mas adelante.
 */
@Entity
@Table(name = "lab_order_tests")
public class LabOrderTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder orden;

    /** Codigo del catalogo de laboratorio, por ejemplo HEM-001. */
    @Column(name = "codigo_prueba", nullable = false, length = 20)
    private String codigoPrueba;

    @Column(name = "nombre_prueba", nullable = false, length = 150)
    private String nombrePrueba;

    @Column(name = "indicaciones", length = 300)
    private String indicaciones;

    protected LabOrderTest() {
        // Requerido por JPA.
    }

    public LabOrderTest(String codigoPrueba, String nombrePrueba, String indicaciones) {
        this.codigoPrueba = codigoPrueba;
        this.nombrePrueba = nombrePrueba;
        this.indicaciones = indicaciones;
    }

    /** Lo invoca LabOrder.agregarPrueba para cerrar la relacion. */
    void asignarA(LabOrder orden) {
        this.orden = orden;
    }

    public Long getId() {
        return id;
    }

    public LabOrder getOrden() {
        return orden;
    }

    public String getCodigoPrueba() {
        return codigoPrueba;
    }

    public String getNombrePrueba() {
        return nombrePrueba;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof LabOrderTest other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LabOrderTest[" + codigoPrueba + "]";
    }
}
