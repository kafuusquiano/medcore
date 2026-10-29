package com.medcore.medical.domain.entity;

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
import java.util.Objects;

/**
 * Diagnostico emitido en una atencion.
 *
 * <p>Guarda la descripcion del codigo CIE-10 ademas del codigo. Es
 * duplicacion deliberada: el catalogo puede corregir una redaccion con el
 * tiempo, y un diagnostico debe conservar el texto con el que se emitio.
 */
@Entity
@Table(name = "diagnoses")
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attention_id", nullable = false)
    private MedicalAttention atencion;

    @Column(name = "codigo_cie10", nullable = false, length = 10)
    private String codigoCie10;

    @Column(name = "descripcion_cie10", nullable = false, length = 300)
    private String descripcionCie10;

    /** True para el diagnostico que motiva la atencion. */
    @Column(name = "principal", nullable = false)
    private boolean principal;

    /** True si es un caso nuevo; false si es el seguimiento de uno previo. */
    @Column(name = "caso_nuevo", nullable = false)
    private boolean casoNuevo = true;

    @Column(name = "notificable", nullable = false)
    private boolean notificable = false;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    protected Diagnosis() {
        // Requerido por JPA.
    }

    public Diagnosis(String codigoCie10, String descripcionCie10, boolean principal,
                     boolean casoNuevo, boolean notificable, String observaciones) {
        this.codigoCie10 = codigoCie10;
        this.descripcionCie10 = descripcionCie10;
        this.principal = principal;
        this.casoNuevo = casoNuevo;
        this.notificable = notificable;
        this.observaciones = observaciones;
    }

    @PrePersist
    void alCrear() {
        this.fechaRegistro = Instant.now();
    }

    /** Lo invoca MedicalAttention.agregarDiagnostico para cerrar la relacion. */
    void asignarA(MedicalAttention atencion) {
        this.atencion = atencion;
    }

    public Long getId() {
        return id;
    }

    public MedicalAttention getAtencion() {
        return atencion;
    }

    public String getCodigoCie10() {
        return codigoCie10;
    }

    public String getDescripcionCie10() {
        return descripcionCie10;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public boolean isCasoNuevo() {
        return casoNuevo;
    }

    public boolean isNotificable() {
        return notificable;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Diagnosis other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Diagnosis[" + codigoCie10 + (principal ? ", principal]" : "]");
    }
}
