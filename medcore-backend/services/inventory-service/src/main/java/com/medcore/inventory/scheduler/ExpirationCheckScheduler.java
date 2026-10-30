package com.medcore.inventory.scheduler;

import com.medcore.inventory.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Revision diaria del almacen.
 *
 * <p>Hace falta un proceso programado porque un lote pasa a vencido sin que
 * nadie lo toque: no hay ninguna peticion que dispare el aviso. El stock bajo
 * si se detecta en cada movimiento, pero un lote que caduca de madrugada no
 * generaria aviso hasta que alguien abriera su ficha.
 *
 * <p>La expresion cron vive en {@code inventory-service.yml} (MC3), de modo
 * que se puede mover la hora sin reconstruir la imagen.
 *
 * <p>LIMITACION: con dos instancias del servicio, las dos correrian la
 * revision y los avisos saldrian duplicados. Antes de escalar hace falta
 * ShedLock, que reparte la ejecucion usando la base como cerrojo. Es la misma
 * limitacion que tiene SlotGenerationScheduler en appointment-service.
 */
@Component
public class ExpirationCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationCheckScheduler.class);

    private final AlertService alertService;

    public ExpirationCheckScheduler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(cron = "${inventory.cron-revision:0 0 6 * * *}")
    public void revisarInventario() {
        // Identificador propio de la ejecucion: no hay peticion HTTP de la que
        // heredar el X-Request-Id, y sin el no se podrian correlacionar en el
        // log los avisos de una misma revision.
        String requestId = "cron-" + UUID.randomUUID();

        log.info("Iniciando revision diaria del inventario [{}]", requestId);

        try {
            var resumen = alertService.revisar(requestId);
            log.info("Revision terminada: {} avisos publicados [{}]",
                    resumen.total(), requestId);

        } catch (RuntimeException e) {
            // Se atrapa aqui para que un fallo no mate el hilo del planificador
            // y deje sin revision todos los dias siguientes.
            log.error("La revision diaria del inventario fallo [{}]: {}",
                    requestId, e.getMessage(), e);
        }
    }
}
