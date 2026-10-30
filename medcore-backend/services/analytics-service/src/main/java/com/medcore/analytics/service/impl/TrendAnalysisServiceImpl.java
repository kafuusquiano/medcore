package com.medcore.analytics.service.impl;

import com.medcore.analytics.domain.dto.response.TrendResponse;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.repository.AnalyticsRecordRepository;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.analytics.service.TrendAnalysisService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Service
public class TrendAnalysisServiceImpl implements TrendAnalysisService {

    /** Variacion por debajo de la cual la tendencia se considera estable. */
    private static final double UMBRAL_ESTABLE = 5.0;

    private final AnalyticsRecordRepository recordRepository;

    public TrendAnalysisServiceImpl(AnalyticsRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Override
    public TrendResponse tendenciaGeneral(ReportPeriod periodo) {
        return construir(periodo, null,
                (desde, hasta) -> recordRepository.serieDiaria(desde, hasta));
    }

    @Override
    public TrendResponse tendenciaDeZona(String zona, ReportPeriod periodo) {
        return construir(periodo, zona,
                (desde, hasta) -> recordRepository.serieDiariaDeZona(desde, hasta, zona));
    }

    @Override
    public TrendResponse tendenciaDePrueba(String codigoPrueba, ReportPeriod periodo) {
        return construir(periodo, codigoPrueba,
                (desde, hasta) -> recordRepository.serieDiariaDePrueba(
                        desde, hasta, codigoPrueba));
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Arma la serie y su comparacion con el periodo anterior.
     *
     * <p>La consulta se recibe como funcion para no repetir tres veces el
     * mismo armado cambiando solo de donde salen los datos.
     */
    private TrendResponse construir(
            ReportPeriod periodo, String filtro,
            BiFunction<LocalDate, LocalDate, List<ConteoPorFecha>> consulta) {

        LocalDate hasta = LocalDate.now();
        LocalDate desde = periodo.desde(hasta);

        List<ConteoPorFecha> diario = consulta.apply(desde, hasta);
        List<TrendResponse.PuntoResponse> puntos = agrupar(diario, desde, hasta, periodo);

        long resultados = diario.stream().mapToLong(ConteoPorFecha::total).sum();
        long casos = diario.stream().mapToLong(ConteoPorFecha::anormales).sum();
        long criticos = diario.stream().mapToLong(ConteoPorFecha::criticos).sum();

        // Periodo anterior de la MISMA duracion, inmediatamente antes: es la
        // unica comparacion que no confunde una subida real con el efecto de
        // mirar una ventana mas larga.
        LocalDate hastaAnterior = desde.minusDays(1);
        LocalDate desdeAnterior = periodo.desde(hastaAnterior);
        long casosAnteriores = consulta.apply(desdeAnterior, hastaAnterior).stream()
                .mapToLong(ConteoPorFecha::anormales)
                .sum();

        return new TrendResponse(
                periodo,
                periodo.etiqueta(),
                desde,
                hasta,
                filtro,
                puntos,
                resultados,
                casos,
                criticos,
                resultados == 0 ? 0 : (double) casos * 100 / resultados,
                compararConAnterior(casos, casosAnteriores));
    }

    /**
     * Agrupa los puntos diarios segun el periodo y rellena los dias sin datos.
     *
     * <p>Rellenar importa: una grafica que salta del dia 3 al dia 9 porque no
     * hubo resultados dibuja una linea recta entre ambos y sugiere una
     * tendencia que no existe. Un dia sin datos es un cero, y hay que verlo.
     */
    private List<TrendResponse.PuntoResponse> agrupar(List<ConteoPorFecha> diario,
                                                      LocalDate desde, LocalDate hasta,
                                                      ReportPeriod periodo) {
        int diasPorPunto = periodo.diasPorPunto();
        List<TrendResponse.PuntoResponse> puntos = new ArrayList<>();

        LocalDate inicio = desde;
        while (!inicio.isAfter(hasta)) {
            LocalDate fin = inicio.plusDays(diasPorPunto - 1L);
            if (fin.isAfter(hasta)) {
                fin = hasta;
            }

            final LocalDate tramoDesde = inicio;
            final LocalDate tramoHasta = fin;

            List<ConteoPorFecha> delTramo = diario.stream()
                    .filter(dia -> !dia.fecha().isBefore(tramoDesde)
                            && !dia.fecha().isAfter(tramoHasta))
                    .toList();

            long resultados = delTramo.stream().mapToLong(ConteoPorFecha::total).sum();
            long casos = delTramo.stream().mapToLong(ConteoPorFecha::anormales).sum();
            long criticos = delTramo.stream().mapToLong(ConteoPorFecha::criticos).sum();

            puntos.add(new TrendResponse.PuntoResponse(
                    tramoDesde,
                    (int) (java.time.temporal.ChronoUnit.DAYS.between(tramoDesde, tramoHasta) + 1),
                    resultados,
                    casos,
                    criticos,
                    resultados == 0 ? 0 : (double) casos * 100 / resultados));

            inicio = fin.plusDays(1);
        }

        return puntos;
    }

    /**
     * Compara con el periodo anterior.
     *
     * <p>Si antes no hubo ningun caso, la variacion es nula y no cero: pasar
     * de 0 a 5 casos no es "subio un 500 por ciento", es que antes no habia
     * con que comparar. Devolver un infinito o un numero inventado haria que
     * el panel mostrara una cifra sin sentido.
     */
    private TrendResponse.TendenciaResponse compararConAnterior(long casos, long anteriores) {
        if (anteriores == 0) {
            return new TrendResponse.TendenciaResponse(
                    0, null, casos > 0 ? "SUBE" : "ESTABLE");
        }

        double variacion = ((double) casos - anteriores) * 100 / anteriores;

        String direccion;
        if (Math.abs(variacion) < UMBRAL_ESTABLE) {
            direccion = "ESTABLE";
        } else if (variacion > 0) {
            direccion = "SUBE";
        } else {
            direccion = "BAJA";
        }

        return new TrendResponse.TendenciaResponse(anteriores, variacion, direccion);
    }
}
