package com.medcore.laboratory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Prueba del catalogo de laboratorio.
 *
 * <p>laboratory-service es el dueno de este catalogo (MC1). medical-service
 * solo guarda el codigo y el nombre con el que se solicito, y el catalogo
 * real vive aqui.
 *
 * <p>La clave primaria es el codigo, como en el CIE-10 de medical-service: ya
 * es unico y estable, y los rangos y resultados lo referencian directamente.
 */
@Entity
@Table(name = "test_catalog")
public class TestCatalog {

    @Id
    @Column(name = "codigo", length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Hematologia, Bioquimica, Microbiologia, Inmunologia... */
    @Column(name = "area", nullable = false, length = 60)
    private String area;

    /** Unidad de medida del resultado; vacia en las pruebas cualitativas. */
    @Column(name = "unidad", length = 20)
    private String unidad;

    /** Tipo de muestra que requiere: SANGRE, ORINA, HECES, ESPUTO... */
    @Column(name = "tipo_muestra", nullable = false, length = 30)
    private String tipoMuestra;

    /**
     * False en las pruebas cualitativas, como un cultivo, cuyo resultado es
     * un texto y no admite comparacion con rangos numericos.
     */
    @Column(name = "cuantitativa", nullable = false)
    private boolean cuantitativa = true;

    /** Preparacion previa del paciente, por ejemplo ayuno de 8 horas. */
    @Column(name = "indicaciones", length = 300)
    private String indicaciones;

    /** Tiempo estimado de entrega, en horas. */
    @Column(name = "tiempo_entrega_horas")
    private Integer tiempoEntregaHoras;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    protected TestCatalog() {
        // Requerido por JPA.
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getArea() {
        return area;
    }

    public String getUnidad() {
        return unidad;
    }

    public String getTipoMuestra() {
        return tipoMuestra;
    }

    public boolean isCuantitativa() {
        return cuantitativa;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public Integer getTiempoEntregaHoras() {
        return tiempoEntregaHoras;
    }

    public boolean isActiva() {
        return activa;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof TestCatalog other && Objects.equals(codigo, other.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codigo);
    }

    @Override
    public String toString() {
        return "TestCatalog[" + codigo + "]";
    }
}
