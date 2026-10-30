package com.medcore.analytics.repository.projection;

/**
 * Resultado de contar casos agrupados por una clave: una zona o una prueba.
 *
 * @param clave      la zona o el codigo de prueba, segun la agregacion
 * @param total      casos contados
 * @param distintos  pacientes distintos entre esos casos. Dice si son muchas
 *                   personas o una sola a la que se repitio la prueba, que es
 *                   la diferencia entre un brote y un seguimiento.
 * @param criticos   casos con valor critico
 */
public record ConteoPorClave(
        String clave,
        long total,
        long distintos,
        long criticos
) {

    /** Proporcion de casos criticos, para ordenar por gravedad. */
    public double proporcionCritica() {
        return total == 0 ? 0 : (double) criticos / total;
    }
}
