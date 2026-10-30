package com.medcore.analytics.scheduler;

import com.medcore.analytics.service.OutbreakDetectionService;
import com.medcore.analytics.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Recalculo periodico de indicadores y barrido completo de brotes.
 *
 * <p>El consumidor ya evalua los brotes de la zona y la prueba de cada caso
 * nuevo, asi que este barrido no es la deteccion principal: es la red de
 * seguridad. Cubre lo que el consumidor no puede ver.
 *
 * <p>Dos casos concretos que solo detecta el barrido:
 * <ul>
 *   <li>Un brote que cruza el umbral porque la ventana AVANZA, no porque
 *       llegue un caso nuevo. Si el umbral baja de 5 a 4 en configuracion, o
 *       si los casos antiguos salen de la ventana y vuelven a entrar otros,
 *       nadie publica un evento que lo dispare.</li>
 *   <li>Casos registrados mientras la deteccion fallaba, por ejemplo con
 *       Mongo momentaneamente inaccesible. El consumidor atrapa el fallo para
 *       no bloquear la particion, y entonces ese caso queda sin evaluar hasta
 *       el barrido siguiente.</li>
 * </ul>
 *
 * <p>La expresion cron vive en {@code analytics-service.yml} (MC3), de modo
 * que se puede cambiar la frecuencia sin reconstruir la imagen.
 *
 * <p>LIMITACION: con dos instancias del servicio, las dos correrian el
 * barrido. El indice unico parcial de una sola alerta abierta por zona y
 * prueba evita que se dupliquen las alertas, pero las fotos de indicadores si
 * se recalcularian dos veces, y el trabajo seria el doble. Antes de escalar
 * hace falta ShedLock. Es la misma limitacion que ExpirationCheckScheduler en
 * inventory-service y SlotGenerationScheduler en appointment-service.
 */
@Component
public class AnalyticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsScheduler.class);

    private final OutbreakDetectionService outbreakService;
    private final ReportService reportService;

    public AnalyticsScheduler(OutbreakDetectionService outbreakService,
                              ReportService reportService) {
        this.outbreakService = outbreakService;
        this.reportService = reportService;
    }

    @Scheduled(cron = "${analytics.cron-indicadores:0 0 * * * *}")
    public void recalcular() {
        // Identificador propio de la ejecucion: no hay peticion HTTP de la que
        // heredar el X-Request-Id, y sin el no se podrian correlacionar en el
        // log las alertas de un mismo barrido.
        String requestId = "cron-" + UUID.randomUUID();

        log.debug("Iniciando barrido de brotes y recalculo de indicadores [{}]", requestId);

        // Las dos tareas van en try separados: un fallo al archivar
        // indicadores no debe dejar sin barrer los brotes, que es lo urgente
        // de las dos.
        try {
            var alertas = outbreakService.detectar(requestId);
            if (!alertas.isEmpty()) {
                log.info("Barrido periodico: {} alerta(s) de brote [{}]",
                        alertas.size(), requestId);
            }
        } catch (RuntimeException e) {
            log.error("El barrido de brotes fallo [{}]: {}", requestId, e.getMessage(), e);
        }

        try {
            reportService.archivarIndicadores(requestId);
        } catch (RuntimeException e) {
            log.error("El archivado de indicadores fallo [{}]: {}",
                    requestId, e.getMessage(), e);
        }
    }
}
