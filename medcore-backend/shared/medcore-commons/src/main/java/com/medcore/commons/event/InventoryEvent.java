package com.medcore.commons.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de los topicos {@code stock.low} y {@code stock.expired}.
 *
 * <p>Los publica inventory-service y los consume notification-service, que
 * avisa al responsable del almacen. Son dos topicos y no uno con una marca
 * porque exigen reacciones distintas: el stock bajo es un aviso para reponer,
 * y un lote vencido obliga a retirar material de la estanteria antes de que
 * alguien lo use.
 *
 * @param cantidadActual  unidades que quedan del lote, o el total del
 *                        reactivo cuando el aviso es de stock bajo
 * @param cantidadSugerida cuanto conviene pedir para llegar al stock optimo;
 *                        cero cuando no hay optimo definido
 * @param diasParaVencer  negativo si el lote ya vencio; nulo en los insumos
 *                        que no caducan
 */
public record InventoryEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idReactivo,
        String codigoReactivo,
        String nombreReactivo,
        String categoria,
        String unidadMedida,
        String proveedor,

        Long idStock,
        String lote,
        Integer cantidadActual,
        Integer stockMinimo,
        Integer cantidadSugerida,
        LocalDate fechaVencimiento,
        Long diasParaVencer,
        String ubicacion
) implements BaseEvent {

    /**
     * El total del reactivo quedo por debajo de su minimo.
     *
     * <p>No lleva lote: el aviso es del reactivo en conjunto, porque lo que
     * importa para reponer es cuanto queda en total, no en que lote esta.
     */
    public static InventoryEvent stockBajo(String requestId, Long idReactivo,
                                           String codigoReactivo, String nombreReactivo,
                                           String categoria, String unidadMedida,
                                           String proveedor, Integer cantidadActual,
                                           Integer stockMinimo, Integer cantidadSugerida) {
        return new InventoryEvent(
                UUID.randomUUID().toString(),
                "stock.low",
                Instant.now(),
                "inventory-service",
                requestId,
                idReactivo, codigoReactivo, nombreReactivo, categoria, unidadMedida, proveedor,
                null, null, cantidadActual, stockMinimo, cantidadSugerida,
                null, null, null);
    }

    /**
     * Un lote vencio, o esta por vencer dentro del plazo de aviso.
     *
     * <p>Lleva lote y ubicacion porque el aviso sirve para ir a la estanteria
     * a retirar ese material concreto.
     */
    public static InventoryEvent loteVencido(String requestId, Long idReactivo,
                                             String codigoReactivo, String nombreReactivo,
                                             String categoria, String unidadMedida,
                                             String proveedor, Long idStock, String lote,
                                             Integer cantidadActual, LocalDate fechaVencimiento,
                                             Long diasParaVencer, String ubicacion) {
        return new InventoryEvent(
                UUID.randomUUID().toString(),
                "stock.expired",
                Instant.now(),
                "inventory-service",
                requestId,
                idReactivo, codigoReactivo, nombreReactivo, categoria, unidadMedida, proveedor,
                idStock, lote, cantidadActual, null, 0,
                fechaVencimiento, diasParaVencer, ubicacion);
    }

    public boolean esStockBajo() {
        return "stock.low".equals(eventType);
    }

    /**
     * Distingue el lote que ya vencio del que esta por vencer. Los dos van al
     * mismo topico, pero el primero obliga a retirar material y el segundo
     * solo a priorizar su uso.
     */
    public boolean yaVencio() {
        return diasParaVencer != null && diasParaVencer < 0;
    }
}
