package com.medcore.laboratory.domain.entity;

import com.medcore.laboratory.domain.enums.ResultInterpretation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Resultado de una prueba sobre una muestra.
 *
 * <p>Es un registro clinico: una vez validado no se modifica. Corregir un
 * resultado consiste en anadir otro que lo rectifique, de modo que el
 * historial conserve lo que se informo en su momento y a partir de que
 * instante se corrigio.
 *
 * <p>El valor puede ser numerico o de texto segun la prueba. Un hemograma da
 * un numero; un cultivo da "Escherichia coli, sensible a ciprofloxacino".
 *
 * <p>El documento crudo del equipo y la trama HL7 original viven en MongoDB:
 * tienen forma variable segun el fabricante y no encajan aqui.
 */
@Entity
@Table(name = "results")
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_id", nullable = false)
    private Sample muestra;

    @Column(name = "codigo_prueba", nullable = false, length = 20)
    private String codigoPrueba;

    @Column(name = "nombre_prueba", nullable = false, length = 150)
    private String nombrePrueba;

    /** Nulo en las pruebas cualitativas. */
    @Column(name = "valor_numerico")
    private Double valorNumerico;

    /** Resultado en texto; en las cuantitativas guarda el valor formateado. */
    @Column(name = "valor_texto", length = 500)
    private String valorTexto;

    @Column(name = "unidad", length = 20)
    private String unidad;

    /** Rango aplicado, copiado al emitir: los rangos del catalogo cambian. */
    @Column(name = "rango_referencia", length = 60)
    private String rangoReferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "interpretacion", nullable = false, length = 20)
    private ResultInterpretation interpretacion = ResultInterpretation.NO_APLICA;

    @Column(name = "critico", nullable = false)
    private boolean critico = false;

    /** Identificador del documento en MongoDB con la trama original. */
    @Column(name = "id_documento_crudo", length = 40)
    private String idDocumentoCrudo;

    /** EQUIPO cuando llega por HL7, MANUAL cuando lo teclea el laboratorista. */
    @Column(name = "origen", nullable = false, length = 20)
    private String origen;

    @Column(name = "registrado_por", length = 120)
    private String registradoPor;

    /** Resultado que este rectifica; nulo si es el primero. */
    @Column(name = "id_resultado_corregido")
    private Long idResultadoCorregido;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "fecha_resultado", nullable = false, updatable = false)
    private Instant fechaResultado;

    protected Result() {
        // Requerido por JPA.
    }

    public Result(Sample muestra, String codigoPrueba, String nombrePrueba,
                  Double valorNumerico, String valorTexto, String unidad,
                  String origen, String registradoPor) {
        this.muestra = muestra;
        this.codigoPrueba = codigoPrueba;
        this.nombrePrueba = nombrePrueba;
        this.valorNumerico = valorNumerico;
        this.valorTexto = valorTexto;
        this.unidad = unidad;
        this.origen = origen;
        this.registradoPor = registradoPor;
    }

    @PrePersist
    void alCrear() {
        this.fechaResultado = Instant.now();
    }

    /**
     * Fija la interpretacion calculada frente a los rangos.
     * Lo hace ValidationService al registrar el resultado.
     */
    public void interpretar(ResultInterpretation interpretacion, String rangoReferencia) {
        this.interpretacion = interpretacion;
        this.rangoReferencia = rangoReferencia;
        this.critico = interpretacion.esCritico();
    }

    public void vincularDocumentoCrudo(String idDocumento) {
        this.idDocumentoCrudo = idDocumento;
    }

    /** Marca este resultado como correccion de otro anterior. */
    public void marcarComoCorreccionDe(Long idResultadoAnterior) {
        this.idResultadoCorregido = idResultadoAnterior;
    }

    public void anotar(String observaciones) {
        this.observaciones = observaciones;
    }

    /**
     * Anade una nota sin borrar la que ya hubiera.
     *
     * <p>Lo usa la deteccion de discrepancias del equipo. Reemplazar la nota
     * perderia la que escribio el laboratorista, y una advertencia tecnica no
     * tiene por que pisar lo que dijo una persona.
     *
     * <p>Si no cabe, se queda lo que ya estaba: la nota del laboratorista vale
     * mas que la advertencia, y la advertencia tambien va al log.
     */
    public void anotarAdicional(String nota) {
        if (nota == null || nota.isBlank()) {
            return;
        }

        if (observaciones == null || observaciones.isBlank()) {
            this.observaciones = recortar(nota);
            return;
        }

        if (observaciones.contains(nota)) {
            return;
        }

        String junto = observaciones + " | " + nota;
        if (junto.length() <= MAX_OBSERVACIONES) {
            this.observaciones = junto;
        }
    }

    /** Tope de la columna observaciones. */
    private static final int MAX_OBSERVACIONES = 500;

    private static String recortar(String texto) {
        return texto.length() <= MAX_OBSERVACIONES
                ? texto
                : texto.substring(0, MAX_OBSERVACIONES);
    }

    public Long getId() {
        return id;
    }

    public Sample getMuestra() {
        return muestra;
    }

    public String getCodigoPrueba() {
        return codigoPrueba;
    }

    public String getNombrePrueba() {
        return nombrePrueba;
    }

    public Double getValorNumerico() {
        return valorNumerico;
    }

    public String getValorTexto() {
        return valorTexto;
    }

    public String getUnidad() {
        return unidad;
    }

    public String getRangoReferencia() {
        return rangoReferencia;
    }

    public ResultInterpretation getInterpretacion() {
        return interpretacion;
    }

    public boolean isCritico() {
        return critico;
    }

    public String getIdDocumentoCrudo() {
        return idDocumentoCrudo;
    }

    public String getOrigen() {
        return origen;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Long getIdResultadoCorregido() {
        return idResultadoCorregido;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Instant getFechaResultado() {
        return fechaResultado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Result other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Result[" + codigoPrueba + "=" + (valorNumerico != null ? valorNumerico : valorTexto)
                + ", " + interpretacion + "]";
    }
}
