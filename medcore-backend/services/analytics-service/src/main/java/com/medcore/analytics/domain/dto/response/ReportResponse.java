package com.medcore.analytics.domain.dto.response;

import com.medcore.analytics.domain.enums.ReportPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Informe epidemiologico de un periodo.
 *
 * <p>Lleva la hora de generacion porque los numeros salen de consultas en
 * vivo: el mismo informe pedido manana puede dar otras cifras si entretanto
 * se corrigio un resultado. Para conservar un valor con el que se tomo una
 * decision estan las fotos de indicadores.
 *
 * @param positividad porcentaje de resultados anormales sobre el total. Es el
 *                    indicador que se sigue en vigilancia: no interesa tanto
 *                    cuantas pruebas se hicieron como que proporcion salio
 *                    alterada.
 */
public record ReportResponse(
        ReportPeriod periodo,
        String etiquetaPeriodo,
        LocalDate desde,
        LocalDate hasta,

        long totalResultados,
        long totalCasos,
        long totalCriticos,
        double positividad,

        long alertasAbiertas,
        long alertasRiesgoAlto,

        List<ConteoResponse> porZona,
        List<ConteoResponse> porPrueba,

        Instant generadoEn
) {

    /**
     * @param clave      zona o codigo de prueba
     * @param porcentaje peso de esta clave sobre el total de casos
     */
    public record ConteoResponse(
            String clave,
            String etiqueta,
            long casos,
            long pacientesDistintos,
            long criticos,
            double porcentaje
    ) {
    }
}
