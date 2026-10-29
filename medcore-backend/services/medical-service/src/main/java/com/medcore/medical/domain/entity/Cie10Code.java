package com.medcore.medical.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Codigo del catalogo CIE-10.
 *
 * <p>Es un catalogo de solo lectura, cargado por cie10_seed.sql. La clave
 * primaria es el propio codigo y no un id autogenerado: el codigo ya es
 * unico y estable a nivel internacional, y asi los diagnosticos lo
 * referencian directamente sin una union adicional.
 */
@Entity
@Table(name = "cie10_codes")
public class Cie10Code {

    @Id
    @Column(name = "codigo", length = 10)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    /** Capitulo del CIE-10, para los informes epidemiologicos. */
    @Column(name = "capitulo", length = 150)
    private String capitulo;

    /** Marca las enfermedades de notificacion obligatoria a la DIRESA. */
    @Column(name = "notificable", nullable = false)
    private boolean notificable = false;

    protected Cie10Code() {
        // Requerido por JPA.
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCapitulo() {
        return capitulo;
    }

    public boolean isNotificable() {
        return notificable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Cie10Code other && Objects.equals(codigo, other.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codigo);
    }

    @Override
    public String toString() {
        return "Cie10Code[" + codigo + "]";
    }
}
