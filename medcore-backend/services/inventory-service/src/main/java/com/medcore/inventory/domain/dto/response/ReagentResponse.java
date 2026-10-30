package com.medcore.inventory.domain.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Vista de un reactivo con su situacion de stock.
 *
 * <p>El total y los lotes van juntos porque es lo que necesita quien abre la
 * ficha: cuanto queda en total para saber si hay que reponer, y en que lotes
 * esta para saber cual gastar primero.
 *
 * @param totalDisponible suma de los lotes vigentes; descarta los retirados y
 *                        los vencidos, porque un almacen lleno de material
 *                        caducado no es stock
 * @param bajoMinimo      calculado, para que el frontend no repita la regla
 * @param cantidadSugerida cuanto conviene pedir para llegar al stock optimo
 * @param lotes           vacio en los listados, relleno en la ficha
 */
public record ReagentResponse(
        Long id,
        String codigo,
        String nombre,
        String categoria,
        String unidadMedida,
        String proveedor,
        Integer stockMinimo,
        Integer stockOptimo,
        Integer totalDisponible,
        boolean bajoMinimo,
        Integer cantidadSugerida,
        boolean requiereRefrigeracion,
        boolean activo,
        String observaciones,
        List<StockResponse> lotes,
        Instant fechaCreacion,
        Instant fechaActualizacion
) {
}
