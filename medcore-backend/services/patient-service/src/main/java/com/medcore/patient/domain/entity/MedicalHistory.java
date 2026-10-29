package com.medcore.patient.domain.entity;

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
 * Episodio del historial clinico del paciente.
 *
 * <p>Es un registro de solo escritura: una vez creado no se modifica ni se
 * borra. Corregir un episodio consiste en anadir otro que lo rectifique, de
 * modo que el historial conserve la secuencia real de lo ocurrido.
 *
 * <p>No contiene el detalle clinico completo, que vive en medical_db y
 * lab_db: aqui queda la linea de tiempo del paciente, con una referencia al
 * servicio y al registro de origen.
 */
@Entity
@Table(name = "medical_history")
public class MedicalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient paciente;

    /** Servicio que origino el episodio: medical-service, laboratory-service... */
    @Column(name = "origen", nullable = false, length = 40)
    private String origen;

    /** Identificador del registro en la base del servicio de origen. */
    @Column(name = "referencia_externa", length = 60)
    private String referenciaExterna;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    /** Codigo CIE-10 cuando el episodio corresponde a un diagnostico. */
    @Column(name = "codigo_cie10", length = 10)
    private String codigoCie10;

    @Column(name = "fecha_episodio", nullable = false)
    private Instant fechaEpisodio;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    protected MedicalHistory() {
        // Requerido por JPA.
    }

    public MedicalHistory(Patient paciente, String origen, String referenciaExterna,
                          String titulo, String descripcion, String codigoCie10,
                          Instant fechaEpisodio) {
        this.paciente = paciente;
        this.origen = origen;
        this.referenciaExterna = referenciaExterna;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.codigoCie10 = codigoCie10;
        // Si no se indica, el episodio ocurrio al registrarlo.
        this.fechaEpisodio = fechaEpisodio == null ? Instant.now() : fechaEpisodio;
    }

    @PrePersist
    void alCrear() {
        this.fechaRegistro = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Patient getPaciente() {
        return paciente;
    }

    public String getOrigen() {
        return origen;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCodigoCie10() {
        return codigoCie10;
    }

    public Instant getFechaEpisodio() {
        return fechaEpisodio;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof MedicalHistory other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "MedicalHistory[id=" + id + ", origen=" + origen + "]";
    }
}
