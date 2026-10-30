package com.medcore.notification.event.consumer;

import com.medcore.commons.event.InventoryEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Escucha {@code stock.low} y {@code stock.expired}.
 *
 * <p>Los dos avisos van al personal del almacen, nunca a un paciente. El
 * correo va al buzon interno porque los eventos de inventario no llevan
 * ningun usuario: el almacen no tiene un responsable asignado en el modelo, y
 * el aviso es para quien este de turno.
 *
 * <p>Por eso estos dos tipos no generan aviso in-app dirigido: sin
 * identificador de usuario no habria bandeja donde ponerlo. STOCK_BAJO va solo
 * in-app segun su tipo, asi que se le pasa el identificador del reactivo como
 * referencia y el aviso queda consultable en el listado general del almacen.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como los metodos atrapan
 * toda RuntimeException, un fallo queda en el log y el offset avanza
 * igualmente, de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    public InventoryEventConsumer(NotificationService notificationService,
                                  NotificationProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @KafkaListener(topics = KafkaTopics.STOCK_LOW, groupId = "notification-service")
    public void alBajarElStock(InventoryEvent evento) {
        try {
            if (evento == null || evento.codigoReactivo() == null) {
                log.warn("Evento stock.low incompleto; se descarta");
                return;
            }

            Map<String, String> datos = base(evento);
            datos.put("cantidad", String.valueOf(evento.cantidadActual()));
            datos.put("minimo", String.valueOf(evento.stockMinimo()));
            datos.put("sugerido", evento.cantidadSugerida() == null
                            || evento.cantidadSugerida() == 0
                    ? "sin determinar"
                    : String.valueOf(evento.cantidadSugerida()));

            notificationService.crear(
                    NotificationType.STOCK_BAJO,
                    evento.eventId(),
                    properties.correoInterno(),
                    null,
                    null,
                    datos);

        } catch (RuntimeException e) {
            log.error("Error procesando stock.low de {}: {}",
                    evento == null ? "desconocido" : evento.codigoReactivo(),
                    e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.STOCK_EXPIRED, groupId = "notification-service")
    public void alVencerUnLote(InventoryEvent evento) {
        try {
            if (evento == null || evento.lote() == null) {
                log.warn("Evento stock.expired incompleto; se descarta");
                return;
            }

            Map<String, String> datos = base(evento);
            datos.put("idStock", String.valueOf(evento.idStock()));
            datos.put("lote", evento.lote());
            datos.put("cantidad", String.valueOf(evento.cantidadActual()));
            datos.put("fechaVencimiento", String.valueOf(evento.fechaVencimiento()));
            datos.put("ubicacion", evento.ubicacion() == null
                    ? "sin ubicacion registrada" : evento.ubicacion());

            notificationService.crear(
                    NotificationType.LOTE_VENCIDO,
                    evento.eventId(),
                    properties.correoInterno(),
                    null,
                    null,
                    datos);

            // El mismo topico trae los lotes ya vencidos y los que estan por
            // vencer, distinguidos por diasParaVencer. El primero exige
            // retirar material de la estanteria hoy; el segundo solo
            // priorizar su uso. El nivel de log lo refleja.
            if (evento.yaVencio()) {
                log.warn("LOTE VENCIDO EN ALMACEN | {} lote={} vencio el {} | "
                                + "quedan {} unidades en {} [{}]",
                        evento.codigoReactivo(), evento.lote(), evento.fechaVencimiento(),
                        evento.cantidadActual(), evento.ubicacion(), evento.requestId());
            } else {
                log.info("Lote por vencer: {} lote={} en {} dias [{}]",
                        evento.codigoReactivo(), evento.lote(),
                        evento.diasParaVencer(), evento.requestId());
            }

        } catch (RuntimeException e) {
            log.error("Error procesando stock.expired del lote {}: {}",
                    evento == null ? "desconocido" : evento.lote(), e.getMessage(), e);
        }
    }

    private Map<String, String> base(InventoryEvent evento) {
        Map<String, String> datos = new HashMap<>();
        datos.put("idReactivo", String.valueOf(evento.idReactivo()));
        datos.put("codigoReactivo", evento.codigoReactivo());
        datos.put("nombreReactivo", evento.nombreReactivo());
        datos.put("categoria", evento.categoria());
        datos.put("unidadMedida", evento.unidadMedida());
        datos.put("proveedor", evento.proveedor() == null
                ? "sin proveedor registrado" : evento.proveedor());
        return datos;
    }
}
