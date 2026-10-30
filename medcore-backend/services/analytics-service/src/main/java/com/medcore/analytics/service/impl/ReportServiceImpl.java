package com.medcore.analytics.service.impl;

import com.medcore.analytics.domain.document.IndicatorSnapshot;
import com.medcore.analytics.domain.dto.response.ReportResponse;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.domain.enums.RiskLevel;
import com.medcore.analytics.exception.AnalyticsException;
import com.medcore.analytics.repository.AnalyticsRecordRepository;
import com.medcore.analytics.repository.IndicatorSnapshotRepository;
import com.medcore.analytics.repository.OutbreakAlertRepository;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private static final int DIAS_MAXIMOS = 366;

    /** Nombres de los indicadores que se archivan a diario. */
    private static final String POSITIVIDAD_GLOBAL = "positividad_global";
    private static final String CASOS_POR_ZONA = "casos_por_zona";
    private static final String CASOS_CRITICOS = "casos_criticos";
    private static final String ALERTAS_ABIERTAS = "alertas_abiertas";

    private final AnalyticsRecordRepository recordRepository;
    private final OutbreakAlertRepository alertRepository;
    private final IndicatorSnapshotRepository snapshotRepository;

    public ReportServiceImpl(AnalyticsRecordRepository recordRepository,
                             OutbreakAlertRepository alertRepository,
                             IndicatorSnapshotRepository snapshotRepository) {
        this.recordRepository = recordRepository;
        this.alertRepository = alertRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public ReportResponse informe(ReportPeriod periodo) {
        LocalDate hasta = LocalDate.now();
        return armar(periodo, periodo.desde(hasta), hasta);
    }

    @Override
    public ReportResponse informeEntre(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw AnalyticsException.rangoInvalido();
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMOS) {
            throw AnalyticsException.rangoDemasiadoAmplio(DIAS_MAXIMOS);
        }

        // Sin periodo nombrado: el rango lo eligio quien pidio el informe.
        return armar(null, desde, hasta);
    }

    @Override
    public List<IndicatorSnapshot> archivarIndicadores(String requestId) {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(ReportPeriod.MENSUAL.dias() - 1L);

        long resultados = recordRepository.contarEntre(desde, hoy);
        long casos = recordRepository.contarAnormalesEntre(desde, hoy);
        long criticos = recordRepository.contarCriticosEntre(desde, hoy);

        List<ConteoPorClave> porZona = recordRepository.casosPorZona(desde, hoy);
        Map<String, Double> desgloseZona = new LinkedHashMap<>();
        porZona.forEach(conteo -> desgloseZona.put(conteo.clave(), (double) conteo.total()));

        Map<String, Double> desgloseAlertas = new LinkedHashMap<>();
        for (RiskLevel nivel : RiskLevel.values()) {
            desgloseAlertas.put(nivel.name(), (double) alertRepository
                    .countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, nivel));
        }

        List<IndicatorSnapshot> fotos = new ArrayList<>();

        fotos.add(guardar(IndicatorSnapshot.de(
                POSITIVIDAD_GLOBAL, hoy,
                "Porcentaje de resultados anormales sobre el total, ultimos 30 dias",
                resultados == 0 ? 0 : (double) casos * 100 / resultados,
                "%", Map.of(), resultados)));

        fotos.add(guardar(IndicatorSnapshot.de(
                CASOS_POR_ZONA, hoy,
                "Casos anormales por zona, ultimos 30 dias",
                (double) casos, "casos", desgloseZona, resultados)));

        fotos.add(guardar(IndicatorSnapshot.de(
                CASOS_CRITICOS, hoy,
                "Resultados criticos, ultimos 30 dias",
                (double) criticos, "casos", Map.of(), resultados)));

        fotos.add(guardar(IndicatorSnapshot.de(
                ALERTAS_ABIERTAS, hoy,
                "Alertas de brote abiertas, por nivel de riesgo",
                (double) alertRepository.countByEstado(AlertStatus.ABIERTA),
                "alertas", desgloseAlertas, resultados)));

        log.info("Indicadores archivados para {}: {} resultados, {} casos, {} criticos [{}]",
                hoy, resultados, casos, criticos, requestId);

        return fotos;
    }

    @Override
    public List<IndicatorSnapshot> fotosDe(String indicador, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw AnalyticsException.rangoInvalido();
        }
        return snapshotRepository.fotosEntre(indicador, desde, hasta);
    }

    @Override
    public IndicatorSnapshot ultimaFotoDe(String indicador) {
        return snapshotRepository.findFirstByIndicadorOrderByPeriodoDesc(indicador)
                .orElseThrow(() -> AnalyticsException.indicadorNoEncontrado(indicador));
    }

    // ---- Apoyo -------------------------------------------------------------

    private ReportResponse armar(ReportPeriod periodo, LocalDate desde, LocalDate hasta) {
        long resultados = recordRepository.contarEntre(desde, hasta);
        long casos = recordRepository.contarAnormalesEntre(desde, hasta);
        long criticos = recordRepository.contarCriticosEntre(desde, hasta);

        return new ReportResponse(
                periodo,
                periodo == null
                        ? desde + " a " + hasta
                        : periodo.etiqueta(),
                desde,
                hasta,
                resultados,
                casos,
                criticos,
                resultados == 0 ? 0 : (double) casos * 100 / resultados,
                alertRepository.countByEstado(AlertStatus.ABIERTA),
                alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.ALTO),
                aConteos(recordRepository.casosPorZona(desde, hasta), casos),
                aConteos(recordRepository.casosPorPrueba(desde, hasta), casos),
                Instant.now());
    }

    /**
     * Convierte los conteos y calcula el peso de cada clave.
     *
     * <p>El porcentaje se calcula sobre el total de casos del periodo y no
     * sobre la suma de los conteos: un paciente puede aparecer en varias
     * pruebas, asi que las dos cifras no coinciden y la que interesa como
     * referencia es el total.
     */
    private List<ReportResponse.ConteoResponse> aConteos(List<ConteoPorClave> conteos,
                                                         long totalCasos) {
        return conteos.stream()
                .map(conteo -> new ReportResponse.ConteoResponse(
                        conteo.clave(),
                        conteo.clave(),
                        conteo.total(),
                        conteo.distintos(),
                        conteo.criticos(),
                        totalCasos == 0 ? 0 : (double) conteo.total() * 100 / totalCasos))
                .toList();
    }

    /**
     * Guarda la foto, sobrescribiendo la del mismo indicador y periodo.
     *
     * <p>Asi el archivado es idempotente por dia: si el scheduler corre cada
     * hora, la foto del dia se actualiza en lugar de acumular veinticuatro
     * copias.
     */
    private IndicatorSnapshot guardar(IndicatorSnapshot foto) {
        var existente = snapshotRepository.findByIndicadorAndPeriodo(
                foto.indicador(), foto.periodo());

        if (existente.isEmpty()) {
            return snapshotRepository.save(foto);
        }

        return snapshotRepository.save(new IndicatorSnapshot(
                existente.get().id(),
                foto.indicador(),
                foto.periodo(),
                foto.descripcion(),
                foto.valor(),
                foto.unidad(),
                foto.desglose(),
                foto.registrosConsiderados(),
                Instant.now()));
    }
}
