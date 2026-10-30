package com.medcore.analytics.domain.dto.response;

import com.medcore.analytics.domain.enums.ReportPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * Serie temporal de casos.
 *
 * @param puntos      agrupados segun el periodo: por dia en las ventanas
 *                    cortas, por semana o por mes en las largas, para que una
 *                    grafica anual no traiga 365 puntos ilegibles
 * @param tendencia   comparacion con el periodo inmediatamente anterior
 */
public record TrendResponse(
        ReportPeriod periodo,
        String etiquetaPeriodo,
        LocalDate desde,
        LocalDate hasta,
        String filtro,
        List<PuntoResponse> puntos,
        long totalResultados,
        long totalCasos,
        long totalCriticos,
        double positividadMedia,
        TendenciaResponse tendencia
) {

    /**
     * @param fecha    inicio del tramo que agrupa el punto
     * @param dias     dias que agrupa; util para que el cliente sepa que un
     *                 punto de una serie anual no es un dia suelto
     */
    public record PuntoResponse(
            LocalDate fecha,
            int dias,
            long resultados,
            long casos,
            long criticos,
            double positividad
    ) {
    }

    /**
     * Variacion respecto al periodo anterior de la misma duracion.
     *
     * @param variacionPorcentual positiva si hay mas casos que antes. Nula
     *                            cuando el periodo anterior no tuvo ningun
     *                            caso: dividir entre cero daria un infinito, y
     *                            "subio un infinito por ciento" no informa de
     *                            nada. El cliente debe mostrar "sin
     *                            comparacion".
     * @param direccion           SUBE, BAJA o ESTABLE
     */
    public record TendenciaResponse(
            long casosPeriodoAnterior,
            Double variacionPorcentual,
            String direccion
    ) {
    }
}
