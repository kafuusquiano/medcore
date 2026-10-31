package com.medcore.inventory.service;

import com.medcore.commons.event.ResultEvent;
import com.medcore.inventory.domain.entity.TestReagentUsage;

import java.util.List;

/**
 * Descuenta del almacen los reactivos que gasta cada prueba realizada.
 *
 * <p>Es lo que mantiene el stock del sistema pegado al real. Antes el consumo
 * habia que apuntarlo a mano, asi que se separaban en cuanto alguien se
 * olvidaba, y la alerta de reposicion llegaba tarde o no llegaba.
 *
 * <p>Se dispara con {@code result.registered}: un resultado registrado es una
 * determinacion hecha, y una determinacion hecha es reactivo gastado. Por
 * evento y no por una llamada del laboratorio, porque el procesamiento de una
 * muestra no puede depender de que el almacen responda.
 */
public interface AutomaticConsumptionService {

    /**
     * Descuenta los reactivos del resultado.
     *
     * <p>Es idempotente por resultado y reactivo: Kafka entrega al menos una
     * vez, y un descuento doble dejaria el stock del sistema por debajo del
     * real, pidiendo reposicion de algo que hay.
     *
     * <p>Nunca lanza por falta de stock. Que el almacen no cuadre no puede
     * bloquear la particion de Kafka ni perder los resultados siguientes: el
     * descuento se registra hasta donde alcanza y el resto queda en el log
     * como aviso para quien lleva el almacen.
     *
     * @return los descuentos aplicados; vacio si la prueba no gasta reactivo o
     *         si el resultado ya se habia procesado
     */
    List<Descuento> descontarPor(ResultEvent evento);

    /** Lineas de consumo de una prueba, para consultar el catalogo. */
    List<TestReagentUsage> consumoDe(String codigoPrueba);

    /** Las pruebas que descuentan solas. */
    List<String> pruebasConConsumo();

    /**
     * Un descuento aplicado.
     *
     * @param aplicado unidades descontadas de verdad, que pueden ser menos de
     *                 las pedidas si no habia stock
     * @param faltante lo que no se pudo descontar; cero cuando alcanzo
     */
    record Descuento(String codigoReactivo, String nombreReactivo,
                     int pedido, int aplicado, int faltante) {

        public boolean completo() {
            return faltante == 0;
        }
    }
}
