package com.medcore.laboratory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Rango de normalidad de una prueba.
 *
 * <p>Depende del sexo y la edad: la hemoglobina normal de un varon adulto no
 * es la de una mujer ni la de un lactante. Por eso una misma prueba tiene
 * varias filas y se elige la que corresponde al paciente.
 *
 * <p>Los limites son abiertos: {@code sexo} nulo significa "cualquiera", y
 * {@code edadMinAnios} o {@code edadMaxAnios} nulos significan sin tope.
 */
@Entity
@Table(name = "reference_ranges")
public class ReferenceRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_prueba", nullable = false, length = 20)
    private String codigoPrueba;

    /** MASCULINO, FEMENINO o nulo para cualquiera. */
    @Column(name = "sexo", length = 20)
    private String sexo;

    @Column(name = "edad_min_anios")
    private Integer edadMinAnios;

    @Column(name = "edad_max_anios")
    private Integer edadMaxAnios;

    @Column(name = "valor_min", nullable = false)
    private Double valorMin;

    @Column(name = "valor_max", nullable = false)
    private Double valorMax;

    protected ReferenceRange() {
        // Requerido por JPA.
    }

    /** Indica si este rango aplica a un paciente concreto. */
    public boolean aplicaA(String sexoPaciente, Integer edadPaciente) {
        if (sexo != null && sexoPaciente != null && !sexo.equalsIgnoreCase(sexoPaciente)) {
            return false;
        }
        if (edadPaciente != null) {
            if (edadMinAnios != null && edadPaciente < edadMinAnios) {
                return false;
            }
            if (edadMaxAnios != null && edadPaciente > edadMaxAnios) {
                return false;
            }
        }
        return true;
    }

    public boolean contiene(double valor) {
        return valor >= valorMin && valor <= valorMax;
    }

    /** Texto que se imprime junto al resultado, por ejemplo "13.5 - 17.5". */
    public String etiqueta() {
        return valorMin + " - " + valorMax;
    }

    public Long getId() {
        return id;
    }

    public String getCodigoPrueba() {
        return codigoPrueba;
    }

    public String getSexo() {
        return sexo;
    }

    public Integer getEdadMinAnios() {
        return edadMinAnios;
    }

    public Integer getEdadMaxAnios() {
        return edadMaxAnios;
    }

    public Double getValorMin() {
        return valorMin;
    }

    public Double getValorMax() {
        return valorMax;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ReferenceRange other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
