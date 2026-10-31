package com.medcore.analytics.domain.enums;

/**
 * De donde sale una alerta de brote.
 *
 * <p>Las dos fuentes se complementan y no son la misma cosa. El laboratorio ve
 * lo que se confirma por analitica; el diagnostico ve lo que el medico
 * determina por clinica. Una enfermedad que no se confirma en laboratorio solo
 * aparece por la segunda, y una infeccion asintomatica detectada en un cribado
 * solo por la primera.
 *
 * <p>No se mezclan en un mismo recuento: el mismo paciente puede tener un
 * resultado anormal y un diagnostico del mismo episodio, y sumarlos contaria
 * dos veces un solo caso.
 */
public enum AlertSource {

    /** Pruebas de laboratorio con resultado anormal en una zona. */
    LABORATORIO("Laboratorio", "pruebas con resultado anormal"),

    /** Diagnosticos CIE-10 registrados por el medico en una zona. */
    DIAGNOSTICO("Diagnostico clinico", "diagnosticos CIE-10 registrados");

    private final String etiqueta;
    private final String descripcion;

    AlertSource(String etiqueta, String descripcion) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String descripcion() {
        return descripcion;
    }
}
