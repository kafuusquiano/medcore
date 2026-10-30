package com.medcore.analytics.service.impl;

import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.domain.dto.response.HeatmapPointResponse;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.exception.AnalyticsException;
import com.medcore.analytics.repository.AnalyticsRecordRepository;
import com.medcore.analytics.repository.OutbreakAlertRepository;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.repository.projection.ZonaProjection;
import com.medcore.analytics.service.EpidemiologicalService;
import com.medcore.commons.event.ResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EpidemiologicalServiceImpl implements EpidemiologicalService {

    private static final Logger log = LoggerFactory.getLogger(EpidemiologicalServiceImpl.class);

    /**
     * Tope de dias que acepta una consulta en vivo.
     *
     * <p>Un rango mayor haria que la agregacion recorriera millones de
     * documentos y dejaria el panel colgado. Para periodos largos estan las
     * fotos de indicadores, que ya vienen calculadas.
     */
    private static final int DIAS_MAXIMOS = 366;

    private final AnalyticsRecordRepository recordRepository;
    private final OutbreakAlertRepository alertRepository;
    private final GeoCentroidCatalog centroides;
    private final ZoneId zonaHoraria = ZoneId.systemDefault();

    public EpidemiologicalServiceImpl(AnalyticsRecordRepository recordRepository,
                                      OutbreakAlertRepository alertRepository,
                                      GeoCentroidCatalog centroides) {
        this.recordRepository = recordRepository;
        this.alertRepository = alertRepository;
        this.centroides = centroides;
    }

    @Override
    public AnalyticsRecord registrar(ResultEvent evento) {
        // Idempotencia: Kafka entrega al menos una vez, y un caso contado doble
        // falsea la vigilancia entera.
        var existente = recordRepository.findByIdResultado(evento.idResultado());
        if (existente.isPresent()) {
            log.debug("El resultado {} ya estaba registrado; se ignora el reenvio [{}]",
                    evento.idResultado(), evento.requestId());
            return existente.get();
        }

        AnalyticsRecord registro = AnalyticsRecord.de(
                evento.idResultado(),
                evento.codigoMuestra(),
                evento.idPaciente(),
                evento.dniPaciente(),
                evento.zonaPaciente(),
                evento.codigoPrueba(),
                evento.nombrePrueba(),
                evento.valorNumerico(),
                evento.unidad(),
                evento.interpretacion(),
                evento.critico(),
                evento.fechaResultado(),
                zonaHoraria);

        try {
            AnalyticsRecord guardado = recordRepository.save(registro);

            log.debug("Caso registrado: {} en {} ({}), anormal={} critico={} [{}]",
                    guardado.codigoPrueba(), guardado.zona(), guardado.interpretacion(),
                    guardado.anormal(), guardado.critico(), evento.requestId());

            return guardado;

        } catch (DuplicateKeyException e) {
            // El indice unico sobre idResultado rechazo la insercion: otro
            // consumidor del mismo grupo llego primero. No es un error.
            log.debug("El resultado {} lo registro otra ejecucion a la vez [{}]",
                    evento.idResultado(), evento.requestId());
            return recordRepository.findByIdResultado(evento.idResultado()).orElse(registro);
        }
    }

    @Override
    public List<HeatmapPointResponse> mapaDeCalor(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);

        List<ConteoPorClave> conteos = recordRepository.casosPorZona(desde, hasta);

        if (conteos.isEmpty()) {
            return List.of();
        }

        // La intensidad es relativa a la zona con mas casos del propio
        // periodo: lo que el mapa muestra es donde se concentran los casos, no
        // una escala absoluta que dependa del volumen del laboratorio.
        long maximo = conteos.stream().mapToLong(ConteoPorClave::total).max().orElse(1);

        Set<String> conAlerta = zonasConAlertaAbierta();

        return conteos.stream()
                .map(conteo -> {
                    var centroide = centroides.de(conteo.clave());
                    return new HeatmapPointResponse(
                            conteo.clave(),
                            conteo.total(),
                            conteo.distintos(),
                            conteo.criticos(),
                            maximo == 0 ? 0 : (double) conteo.total() / maximo,
                            conAlerta.contains(conteo.clave()),
                            centroide.map(GeoCentroidCatalog.Centroide::latitud).orElse(null),
                            centroide.map(GeoCentroidCatalog.Centroide::longitud).orElse(null));
                })
                .toList();
    }

    @Override
    public List<AnalyticsRecord> casosDeZona(String zona, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return recordRepository.casosDeZonaEntre(zona, desde, hasta);
    }

    @Override
    public List<AnalyticsRecord> criticos(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return recordRepository.criticosEntre(desde, hasta);
    }

    @Override
    public List<String> zonas() {
        return recordRepository.zonasConRegistros().stream()
                .map(ZonaProjection::zona)
                .filter(zona -> zona != null && !zona.isBlank())
                .toList();
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Zonas con alguna alerta abierta, para marcarlas en el mapa.
     *
     * <p>Se consultan de una vez y no por zona: son pocas alertas abiertas, y
     * preguntar por cada punto del mapa seria una consulta por zona.
     */
    private Set<String> zonasConAlertaAbierta() {
        Set<String> zonas = new HashSet<>();
        alertRepository.findByEstadoOrderByNivelRiesgoDescFechaDeteccionDesc(AlertStatus.ABIERTA)
                .forEach(alerta -> zonas.add(alerta.zona()));
        return zonas;
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw AnalyticsException.rangoInvalido();
        }
        if (desde.isAfter(hasta)) {
            throw AnalyticsException.rangoInvalido();
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMOS) {
            throw AnalyticsException.rangoDemasiadoAmplio(DIAS_MAXIMOS);
        }
    }
}
