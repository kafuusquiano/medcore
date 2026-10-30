package com.medcore.inventory.event.producer;

import com.medcore.commons.event.InventoryEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica {@code stock.low} y {@code stock.expired}.
 *
 * <p>Los consume notification-service, que avisa al responsable del almacen.
 * Son dos topicos y no uno con una marca porque exigen reacciones distintas:
 * el stock bajo es un aviso para reponer, y un lote vencido obliga a retirar
 * material de la estanteria antes de que alguien lo use.
 *
 * <p>La clave de particion es el codigo del reactivo, de modo que los avisos
 * de un mismo reactivo lleguen en orden: si en la misma revision se avisa de
 * stock bajo y acto seguido entra material, el consumidor los ve en el orden
 * correcto.
 */
@Component
public class InventoryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * El total disponible de un reactivo quedo por debajo de su minimo.
     *
     * @param totalDisponible suma de los lotes vigentes, ya calculada
     */
    public void publicarStockBajo(Reagent reactivo, int totalDisponible, String requestId) {
        InventoryEvent evento = InventoryEvent.stockBajo(
                requestId,
                reactivo.getId(),
                reactivo.getCodigo(),
                reactivo.getNombre(),
                reactivo.getCategoria(),
                reactivo.getUnidadMedida(),
                reactivo.getProveedor(),
                totalDisponible,
                reactivo.getStockMinimo(),
                reactivo.cantidadSugeridaDePedido(totalDisponible));

        enviar(KafkaTopics.STOCK_LOW, reactivo.getCodigo(), evento, "stock.low",
                reactivo.getCodigo());
    }

    /**
     * Un lote vencio, o esta por vencer dentro del plazo de aviso.
     *
     * <p>Los dos casos van al mismo topico y se distinguen por
     * {@code diasParaVencer}: negativo si ya vencio. El consumidor decide si
     * el aviso es "retira este material" o "gasta este lote primero".
     */
    public void publicarLoteVencido(Stock lote, String requestId) {
        Reagent reactivo = lote.getReactivo();

        InventoryEvent evento = InventoryEvent.loteVencido(
                requestId,
                reactivo.getId(),
                reactivo.getCodigo(),
                reactivo.getNombre(),
                reactivo.getCategoria(),
                reactivo.getUnidadMedida(),
                reactivo.getProveedor(),
                lote.getId(),
                lote.getLote(),
                lote.getCantidadActual(),
                lote.getFechaVencimiento(),
                lote.diasHastaVencimiento(),
                lote.getUbicacion());

        enviar(KafkaTopics.STOCK_EXPIRED, reactivo.getCodigo(), evento, "stock.expired",
                reactivo.getCodigo() + " lote " + lote.getLote());
    }

    /**
     * Envia el evento sin esperar la confirmacion.
     *
     * <p>Un fallo se registra pero no se propaga: el aviso es informativo y
     * hacer fallar la revision nocturna por no poder publicarlo dejaria sin
     * revisar los reactivos siguientes. El detalle queda en el log, y la
     * misma revision del dia siguiente volvera a detectar lo que siga
     * pendiente, porque la situacion se calcula cada vez y no se guarda.
     */
    private void enviar(String topico, String clave, InventoryEvent evento,
                        String tipo, String detalle) {
        kafkaTemplate.send(topico, clave, evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar {} de {}: {}",
                                tipo, detalle, error.getMessage());
                    } else {
                        log.debug("Publicado {} de {} en la particion {}",
                                tipo, detalle, resultado.getRecordMetadata().partition());
                    }
                });
    }
}
