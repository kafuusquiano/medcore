package com.medcore.laboratory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Limites criticos de una prueba: los valores que obligan a avisar al medico
 * de inmediato porque comprometen la vida del paciente.
 *
 * <p>Son distintos del rango de referencia. Fuera del rango de referencia el
 * valor es anormal; fuera del limite critico es una urgencia. Entre ambos hay
 * una franja de valores anormales que no requieren aviso inmediato.
 *
 * <p>Ambos limites son opcionales: hay pruebas con solo un extremo critico,
 * como las plaquetas, peligrosas solo cuando estan muy bajas.
 */
@Entity
@Table(name = "critical_values")
public class CriticalValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_prueba", nullable = false, unique = true, length = 20)
    private String codigoPrueba;

    /** Por debajo de este valor se considera critico bajo; nulo si no aplica. */
    @Column(name = "limite_inferior")
    private Double limiteInferior;

    /** Por encima de este valor se considera critico alto; nulo si no aplica. */
    @Column(name = "limite_superior")
    private Double limiteSuperior;

    @Column(name = "indicacion", length = 300)
    private String indicacion;

    protected CriticalValue() {
        // Requerido por JPA.
    }

    public boolean esCriticoBajo(double valor) {
        return limiteInferior != null && valor < limiteInferior;
    }

    public boolean esCriticoAlto(double valor) {
        return limiteSuperior != null && valor > limiteSuperior;
    }

    public Long getId() {
        return id;
    }

    public String getCodigoPrueba() {
        return codigoPrueba;
    }

    public Double getLimiteInferior() {
        return limiteInferior;
    }

    public Double getLimiteSuperior() {
        return limiteSuperior;
    }

    public String getIndicacion() {
        return indicacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof CriticalValue other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
