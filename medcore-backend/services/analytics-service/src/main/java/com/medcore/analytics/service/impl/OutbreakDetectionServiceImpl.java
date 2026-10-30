package com.medcore.analytics.service.impl;

import com.medcore.analytics.config.AnalyticsProperties;
import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.domain.document.OutbreakAlert;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.RiskLevel;
import com.medcore.analytics.exception.AnalyticsException;
import com.medcore.analytics.repository.AnalyticsRecordRepository;
import com.medcore.analytics.repository.OutbreakAlertRepository;
import com.medcore.analytics.repository.projection.ConteoPorZonaYPrueba;
import com.medcore.analytics.service.OutbreakDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class OutbreakDetectionServiceImpl implements OutbreakDetectionService {

    private static final Logger log = LoggerFactory.getLogger(OutbreakDetectionServiceImpl.class);

    private final AnalyticsRecordRepository recordRepository;
    private final OutbreakAlertRepository alertRepository;
    private final AnalyticsProperties properties;

    public OutbreakDetectionServiceImpl(AnalyticsRecordRepository recordRepository,
                                        OutbreakAlertRepository alertRepository,
                                        AnalyticsProperties properties) {
        this.recordRepository = recordRepository;
        this.alertRepository = alertRepository;
        this.properties = properties;
    }

    @Override
    public List<OutbreakAlert> detectar(String requestId) {
        int umbral = properties.umbralCasos();
        int ventana = properties.ventanaDias();

        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(ventana - 1L);

        // Una sola agregacion devuelve todas las combinaciones de zona y
        // prueba que llegan al umbral. Preguntar zona por zona exigiria una
        // consulta por cada par existente.
        List<ConteoPorZonaYPrueba> candidatos =
                recordRepository.casosPorZonaYPrueba(desde, hasta, umbral);

        List<OutbreakAlert> afectadas = new ArrayList<>();

        for (ConteoPorZonaYPrueba candidato : candidatos) {
            // Cada candidato en su propio try: si uno falla, los demas deben
            // evaluarse igual. Un barrido que se corta en la tercera zona deja
            // sin vigilar las veinte siguientes.
            try {
                abrirOActualizar(candidato, umbral, ventana, requestId)
                        .ifPresent(afectadas::add);
            } catch (RuntimeException e) {
                log.error("No se pudo evaluar el brote de {} / {}: {}",
                        candidato.zona(), candidato.codigoPrueba(), e.getMessage(), e);
            }
        }

        if (afectadas.isEmpty()) {
            log.debug("Barrido de brotes sin novedades: umbral {} casos en {} dias [{}]",
                    umbral, ventana, requestId);
        } else {
            log.info("Barrido de brotes: {} alerta(s) abierta(s) o actualizada(s) "
                            + "con umbral {} en {} dias [{}]",
                    afectadas.size(), umbral, ventana, requestId);
        }

        return afectadas;
    }

    @Override
    public List<OutbreakAlert> evaluar(String zona, String codigoPrueba, String requestId) {
        int umbral = properties.umbralCasos();
        int ventana = properties.ventanaDias();

        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(ventana - 1L);

        List<AnalyticsRecord> casos = recordRepository.casosAnormalesDe(
                zona, codigoPrueba, desde, hasta);

        // Pacientes distintos, no resultados: a una persona a la que se repite
        // la prueba tres veces en la semana no se la puede contar como tres
        // casos.
        Set<String> pacientes = new TreeSet<>();
        casos.forEach(caso -> pacientes.add(caso.dniPaciente()));

        if (pacientes.size() < umbral) {
            return List.of();
        }

        long criticos = casos.stream().filter(AnalyticsRecord::critico).count();
        LocalDate primero = casos.stream().map(AnalyticsRecord::fecha)
                .min(Comparator.naturalOrder()).orElse(desde);
        LocalDate ultimo = casos.stream().map(AnalyticsRecord::fecha)
                .max(Comparator.naturalOrder()).orElse(hasta);

        ConteoPorZonaYPrueba conteo = new ConteoPorZonaYPrueba(
                zona,
                codigoPrueba,
                casos.isEmpty() ? codigoPrueba : casos.get(0).nombrePrueba(),
                pacientes.size(),
                (int) criticos,
                List.copyOf(pacientes),
                primero,
                ultimo);

        return abrirOActualizar(conteo, umbral, ventana, requestId)
                .map(List::of)
                .orElseGet(List::of);
    }

    @Override
    public OutbreakAlert resolver(String id, AlertStatus desenlace, String usuario, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw AnalyticsException.motivoResolucionRequerido();
        }

        OutbreakAlert alerta = obtenerPorId(id);

        if (!alerta.estado().puedePasarA(desenlace)) {
            throw AnalyticsException.transicionInvalida(
                    alerta.estado().name(), desenlace.name());
        }

        OutbreakAlert resuelta = alertRepository.save(
                alerta.resolver(desenlace, usuario == null ? "sistema" : usuario, motivo));

        log.info("Alerta de {} / {} cerrada como {} por {}: {}",
                resuelta.zona(), resuelta.codigoPrueba(), desenlace,
                resuelta.resueltaPor(), motivo);

        return resuelta;
    }

    @Override
    public List<OutbreakAlert> alertasAbiertas() {
        return alertRepository
                .findByEstadoOrderByNivelRiesgoDescFechaDeteccionDesc(AlertStatus.ABIERTA);
    }

    @Override
    public List<OutbreakAlert> alertasPorNivel(RiskLevel nivel) {
        return alertRepository.findByEstadoAndNivelRiesgoOrderByFechaDeteccionDesc(
                AlertStatus.ABIERTA, nivel);
    }

    @Override
    public List<OutbreakAlert> alertasDeZona(String zona) {
        return alertRepository.findByZonaOrderByFechaDeteccionDesc(zona);
    }

    @Override
    public OutbreakAlert obtenerPorId(String id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> AnalyticsException.alertaNoEncontrada(id));
    }

    @Override
    public Resumen resumen() {
        return new Resumen(
                alertRepository.countByEstado(AlertStatus.ABIERTA),
                alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.ALTO),
                alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.MEDIO),
                alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.BAJO));
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Abre la alerta, o actualiza la que ya este abierta.
     *
     * <p>Mientras una alerta sigue ABIERTA no se abre otra igual: los casos
     * nuevos actualizan sus cifras. Sin esa regla, cada caso del mismo brote
     * abriria una alerta y el panel acabaria con veinte avisos del mismo
     * hecho.
     *
     * <p>Devuelve vacio si la alerta existia y sus cifras no cambiaron, para
     * que el barrido no informe de movimiento donde no lo hubo.
     */
    private Optional<OutbreakAlert> abrirOActualizar(ConteoPorZonaYPrueba conteo, int umbral,
                                                     int ventana, String requestId) {
        Optional<OutbreakAlert> abierta = alertRepository.findByZonaAndCodigoPruebaAndEstado(
                conteo.zona(), conteo.codigoPrueba(), AlertStatus.ABIERTA);

        if (abierta.isPresent()) {
            OutbreakAlert actual = abierta.get();

            if (actual.casos() == conteo.casos()
                    && actual.casosCriticos() == conteo.criticos()) {
                return Optional.empty();
            }

            OutbreakAlert actualizada = alertRepository.save(actual.actualizar(
                    conteo.casos(), conteo.criticos(), conteo.dnisSeguros(),
                    conteo.primerCaso(), conteo.ultimoCaso()));

            log.info("Alerta de {} / {} actualizada: {} -> {} casos, riesgo {} [{}]",
                    conteo.zona(), conteo.codigoPrueba(), actual.casos(),
                    actualizada.casos(), actualizada.nivelRiesgo(), requestId);

            return Optional.of(actualizada);
        }

        OutbreakAlert nueva = OutbreakAlert.abrir(
                conteo.zona(), conteo.codigoPrueba(), conteo.nombrePrueba(),
                conteo.casos(), conteo.criticos(), umbral, ventana,
                conteo.dnisSeguros(), conteo.primerCaso(), conteo.ultimoCaso());

        try {
            OutbreakAlert guardada = alertRepository.save(nueva);

            // Nivel de log segun la gravedad: una alerta de riesgo alto tiene
            // que destacar entre el resto del log, porque es la que exige que
            // alguien actue hoy.
            if (guardada.nivelRiesgo().exigeAvisoInmediato()) {
                log.warn("POSIBLE BROTE | zona={} prueba={} casos={} (umbral {}) "
                                + "criticos={} ventana={} dias | riesgo ALTO [{}]",
                        guardada.zona(), guardada.codigoPrueba(), guardada.casos(),
                        umbral, guardada.casosCriticos(), ventana, requestId);
            } else {
                log.info("Alerta de brote abierta | zona={} prueba={} casos={} riesgo={} [{}]",
                        guardada.zona(), guardada.codigoPrueba(), guardada.casos(),
                        guardada.nivelRiesgo(), requestId);
            }

            return Optional.of(guardada);

        } catch (DuplicateKeyException e) {
            // El indice unico parcial de la coleccion rechazo una segunda
            // alerta abierta para la misma zona y prueba. Ocurre si el
            // scheduler y el consumidor evaluan lo mismo a la vez: no es un
            // error, solo significa que el otro llego primero.
            log.debug("Ya existia una alerta abierta de {} / {}; la creo otra ejecucion [{}]",
                    conteo.zona(), conteo.codigoPrueba(), requestId);
            return Optional.empty();
        }
    }
}
