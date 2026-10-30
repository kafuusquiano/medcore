package com.medcore.inventory.domain.dto.response;

import com.medcore.inventory.domain.enums.StockStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Vista de un lote en almacen.
 *
 * @param situacion        calculada en el momento a partir de la cantidad, el
 *                         minimo del reactivo y la fecha de vencimiento. No
 *                         se guarda en la base: un lote pasa a vencido sin que
 *                         nadie lo toque, y una columna desactualizada diria
 *                         que hay material usable cuando ya no lo es.
 * @param diasParaVencer   negativo si ya vencio; nulo si el lote no caduca
 * @param consumible       false si esta retirado, vencido o sin unidades
 * @param unidadesConsumidas diferencia entre la cantidad inicial y la actual
 */
public record StockResponse(
        Long id,
        Long idReactivo,
        String codigoReactivo,
        String nombreReactivo,
        String categoria,
        String unidadMedida,
        String lote,
        Integer cantidadActual,
        Integer cantidadInicial,
        Integer unidadesConsumidas,
        Integer stockMinimo,
        LocalDate fechaVencimiento,
        Long diasParaVencer,
        LocalDate fechaIngreso,
        String ubicacion,
        boolean requiereRefrigeracion,
        StockStatus situacion,
        String etiquetaSituacion,
        boolean consumible,
        boolean retirado,
        String observaciones,
        Instant fechaActualizacion
) {
}
