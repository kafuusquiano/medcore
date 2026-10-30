package com.medcore.analytics.repository.projection;

import java.time.LocalDate;

/**
 * Un punto de una serie temporal.
 *
 * @param total      resultados registrados ese dia
 * @param anormales  los que cuentan como caso
 * @param criticos   los que exigieron aviso inmediato
 */
public record ConteoPorFecha(
        LocalDate fecha,
        long total,
        long anormales,
        long criticos
) {

    /**
     * Porcentaje de positividad del dia.
     *
     * <p>Es el indicador que se sigue en vigilancia: no interesa tanto cuantas
     * pruebas se hicieron como que proporcion salio alterada. Un pico de
     * casos puede ser solo un dia de mucho trabajo; un pico de positividad,
     * no.
     */
    public double positividad() {
        return total == 0 ? 0 : (double) anormales * 100 / total;
    }
}
