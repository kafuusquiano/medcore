package com.medcore.analytics.repository.projection;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de una prueba anormal en una zona, dentro de una ventana.
 *
 * <p>Es lo que el detector de brotes evalua contra el umbral.
 *
 * @param casos      pacientes DISTINTOS con resultado anormal, no documentos:
 *                   a una persona a la que se repite la prueba tres veces en
 *                   la semana no se la puede contar como tres casos
 * @param dnis       documentos de esos pacientes, para rastrear el brote
 */
public record ConteoPorZonaYPrueba(
        String zona,
        String codigoPrueba,
        String nombrePrueba,
        int casos,
        int criticos,
        List<String> dnis,
        LocalDate primerCaso,
        LocalDate ultimoCaso
) {

    public List<String> dnisSeguros() {
        return dnis == null ? List.of() : List.copyOf(dnis);
    }
}
