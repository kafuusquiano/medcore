package com.medcore.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del servicio, de {@code infra/config-repo/inventory-service.yml}.
 *
 * <p>Se leen con {@code @ConfigurationProperties} y no con {@code @Value}
 * porque el config-server entrega las listas como claves indexadas y
 * {@code @Value} falla al resolverlas. Es el mismo motivo por el que el
 * gateway usa GatewayProperties.
 *
 * @param alertaVencimientoDias antelacion con la que se avisa de un lote por
 *                              vencer. 30 dias es el valor de partida: da
 *                              margen para gastar el lote antes de perderlo.
 * @param cronRevision          cuando corre la revision diaria. Vive en la
 *                              configuracion y no en la anotacion para poder
 *                              cambiarlo sin reconstruir la imagen.
 */
@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        Integer alertaVencimientoDias,
        String cronRevision
) {

    private static final int DIAS_POR_OMISION = 30;

    /**
     * Dias de aviso, con valor por omision si la configuracion no los trae.
     *
     * <p>Sin este respaldo, un arranque con el config-server a medias dejaria
     * el plazo en null y la revision fallaria con NullPointerException en
     * lugar de avisar de lo que toca.
     */
    public int diasDeAviso() {
        return alertaVencimientoDias == null || alertaVencimientoDias < 0
                ? DIAS_POR_OMISION
                : alertaVencimientoDias;
    }
}
