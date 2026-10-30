package com.medcore.inventory;

import com.medcore.inventory.config.InventoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * inventory-service (:8086): reactivos, stock por lote y alertas.
 *
 * <p>Base propia inventory_db (MC1). No consume ningun topico: publica
 * stock.low y stock.expired, que lee notification-service.
 *
 * <p>El stock se lleva por lote y no por reactivo porque es lo que un
 * laboratorio necesita: hay que gastar primero lo que caduca antes, y cuando
 * un lote sale defectuoso el proveedor lo reclama por su numero de lote.
 *
 * <p>@EnableScheduling por la revision diaria de vencimientos: un lote pasa a
 * vencido sin que nadie lo toque, asi que no hay peticion que dispare el
 * aviso.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
