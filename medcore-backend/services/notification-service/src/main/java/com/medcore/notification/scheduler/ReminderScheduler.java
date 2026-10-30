package com.medcore.notification.scheduler;

import com.medcore.notification.service.impl.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Procesa la cola de envio periodicamente.
 *
 * <p>Los avisos se intentan enviar en el acto al crearlos, asi que este
 * proceso no es la via principal. Recoge dos cosas que esa via no puede:
 *
 * <ul>
 *   <li>Los que FALLARON. Un corte del servidor de correo deja el aviso en
 *       FALLIDA, y sin este proceso nadie volveria a intentarlo.</li>
 *   <li>Los PROGRAMADOS. El recordatorio de cita se crea al reservar y tiene
 *       que salir el dia antes; no hay ningun evento que ocurra entonces para
 *       dispararlo.</li>
 * </ul>
 *
 * <p>Corre cada minuto. Es frecuente a proposito: un aviso urgente que fallo
 * por un corte de red de diez segundos no deberia esperar una hora, y la
 * consulta de la cola usa un indice parcial sobre lo pendiente, que es una
 * fraccion minima de la tabla.
 *
 * <p>LIMITACION: con dos instancias del servicio, las dos procesarian la misma
 * cola y un aviso podria enviarse dos veces. El bloqueo optimista de la
 * entidad lo reduce, porque la segunda instancia choca al guardar, pero el
 * correo ya habria salido. Antes de escalar hace falta ShedLock. Es la misma
 * limitacion que ExpirationCheckScheduler en inventory-service,
 * AnalyticsScheduler en analytics-service y SlotGenerationScheduler en
 * appointment-service.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final NotificationDispatcher dispatcher;

    public ReminderScheduler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(cron = "${notification.cron-envio:0 * * * * *}")
    public void procesarCola() {
        try {
            var resumen = dispatcher.procesarCola();

            // Solo se registra cuando hubo trabajo: corriendo cada minuto, una
            // linea por pasada vacia llenaria el log de ruido y esconderia las
            // que importan.
            if (resumen.total() > 0) {
                log.debug("Pasada de la cola: {} enviados, {} fallidos, {} omitidos",
                        resumen.enviados(), resumen.fallidos(), resumen.omitidos());
            }

        } catch (RuntimeException e) {
            // Se atrapa aqui para que un fallo no mate el hilo del
            // planificador y deje la cola sin procesar indefinidamente.
            log.error("La pasada de la cola de avisos fallo: {}", e.getMessage(), e);
        }
    }
}
