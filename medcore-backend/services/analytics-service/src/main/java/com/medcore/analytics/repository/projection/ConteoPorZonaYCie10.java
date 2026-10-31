package com.medcore.analytics.repository.projection;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de un codigo CIE-10 en una zona y un periodo.
 *
 * @param casos pacientes DISTINTOS, no diagnosticos. Un paciente que vuelve a
 *              consulta por lo mismo genera un diagnostico mas, y contarlo dos
 *              veces inventaria la mitad de un brote
 * @param dnis  los pacientes del grupo, para poder comprobar un recuento sin
 *              rehacer la consulta
 */
public record ConteoPorZonaYCie10(
        String zona,
        String codigoCie10,
        String descripcionCie10,
        int casos,
        int notificables,
        List<String> dnis,
        LocalDate primerCaso,
        LocalDate ultimoCaso
) {

    public List<String> dnisSeguros() {
        return dnis == null ? List.of() : List.copyOf(dnis);
    }
}
