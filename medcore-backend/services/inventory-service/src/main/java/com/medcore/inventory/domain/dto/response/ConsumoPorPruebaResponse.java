package com.medcore.inventory.domain.dto.response;

import com.medcore.inventory.domain.entity.TestReagentUsage;

/**
 * Un reactivo que gasta una prueba, y cuanto.
 *
 * @param cantidadPorPrueba unidades por UNA determinacion, no por orden
 * @param stockMinimo       el minimo del reactivo, para ver de un vistazo si
 *                          el consumo de esta prueba lo va a tensar
 */
public record ConsumoPorPruebaResponse(
        Long id,
        String codigoPrueba,
        String codigoReactivo,
        String nombreReactivo,
        String unidadMedida,
        Integer cantidadPorPrueba,
        Integer stockMinimo,
        boolean activo,
        String observaciones
) {

    public static ConsumoPorPruebaResponse de(TestReagentUsage uso) {
        var reactivo = uso.getReactivo();

        return new ConsumoPorPruebaResponse(
                uso.getId(),
                uso.getCodigoPrueba(),
                reactivo.getCodigo(),
                reactivo.getNombre(),
                reactivo.getUnidadMedida(),
                uso.getCantidadPorPrueba(),
                reactivo.getStockMinimo(),
                uso.isActivo(),
                uso.getObservaciones());
    }
}
