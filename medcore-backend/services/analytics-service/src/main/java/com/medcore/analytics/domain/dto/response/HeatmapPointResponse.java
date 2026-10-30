package com.medcore.analytics.domain.dto.response;

/**
 * Un punto del mapa de calor: una zona con sus casos.
 *
 * @param pacientesDistintos dice si los casos son de muchas personas o de una
 *                           sola a la que se repitio la prueba, que es la
 *                           diferencia entre un brote y un seguimiento
 * @param intensidad         valor de 0 a 1 para el color del mapa, relativo a
 *                           la zona con mas casos del mismo periodo. Se
 *                           calcula en el servidor para que todos los clientes
 *                           pinten la misma escala.
 * @param latitud            centroide aproximado de la zona; nulo si la zona
 *                           no esta en el archivo de referencia
 */
public record HeatmapPointResponse(
        String zona,
        long casos,
        long pacientesDistintos,
        long casosCriticos,
        double intensidad,
        boolean tieneAlertaAbierta,
        Double latitud,
        Double longitud
) {
}
