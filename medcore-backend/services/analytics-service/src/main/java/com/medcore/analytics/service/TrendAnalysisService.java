package com.medcore.analytics.service;

import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.domain.dto.response.TrendResponse;

/**
 * Series temporales de casos.
 *
 * <p>Los puntos se agrupan segun el periodo: por dia en las ventanas cortas y
 * por semana o por mes en las largas. Una serie anual con un punto por dia son
 * 365 puntos que en una grafica no se leen.
 */
public interface TrendAnalysisService {

    /** Serie de todo el sistema. */
    TrendResponse tendenciaGeneral(ReportPeriod periodo);

    /** Serie limitada a una zona. */
    TrendResponse tendenciaDeZona(String zona, ReportPeriod periodo);

    /** Serie limitada a una prueba. */
    TrendResponse tendenciaDePrueba(String codigoPrueba, ReportPeriod periodo);
}
