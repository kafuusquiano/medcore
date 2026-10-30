package com.medcore.analytics.service;

import com.medcore.analytics.domain.document.IndicatorSnapshot;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.domain.dto.response.ReportResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Informes e indicadores.
 *
 * <p>Los informes se calculan en vivo; las fotos de indicadores conservan el
 * valor que tenia un indicador en un momento dado. Hacen falta las dos cosas:
 * un indicador calculado sobre los registros vivos cambia si un resultado se
 * corrige la semana siguiente, y entonces el porcentaje con el que se tomo una
 * decision deja de ser reproducible.
 */
public interface ReportService {

    ReportResponse informe(ReportPeriod periodo);

    ReportResponse informeEntre(LocalDate desde, LocalDate hasta);

    /**
     * Calcula y archiva las fotos de indicadores del dia.
     *
     * <p>Lo llama el scheduler. Es idempotente por dia: volver a calcularlo
     * sobrescribe la foto del mismo periodo en lugar de acumular duplicados.
     */
    List<IndicatorSnapshot> archivarIndicadores(String requestId);

    List<IndicatorSnapshot> fotosDe(String indicador, LocalDate desde, LocalDate hasta);

    IndicatorSnapshot ultimaFotoDe(String indicador);
}
