package com.medcore.inventory.mapper;

import com.medcore.inventory.domain.dto.response.MovementResponse;
import com.medcore.inventory.domain.dto.response.StockResponse;
import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.enums.StockStatus;
import org.springframework.stereotype.Component;

/** Conversion de lotes y movimientos a sus vistas. */
@Component
public class StockMapper {

    /**
     * Vista de un lote.
     *
     * <p>El plazo de aviso de vencimiento se recibe como parametro y no se
     * lee aqui: vive en la configuracion del servicio (MC3), y pasarlo
     * explicitamente deja claro que la situacion depende de un valor
     * configurable y no de una constante del codigo.
     */
    public StockResponse aRespuesta(Stock lote, int diasAvisoVencimiento) {
        Reagent reactivo = lote.getReactivo();
        StockStatus situacion = lote.situacion(diasAvisoVencimiento);

        return new StockResponse(
                lote.getId(),
                reactivo.getId(),
                reactivo.getCodigo(),
                reactivo.getNombre(),
                reactivo.getCategoria(),
                reactivo.getUnidadMedida(),
                lote.getLote(),
                lote.getCantidadActual(),
                lote.getCantidadInicial(),
                lote.unidadesConsumidas(),
                reactivo.getStockMinimo(),
                lote.getFechaVencimiento(),
                lote.diasHastaVencimiento(),
                lote.getFechaIngreso(),
                lote.getUbicacion(),
                reactivo.isRequiereRefrigeracion(),
                situacion,
                situacion.etiqueta(),
                lote.esConsumible(),
                lote.isRetirado(),
                lote.getObservaciones(),
                lote.getFechaActualizacion());
    }

    public MovementResponse aRespuesta(MovementLog movimiento) {
        return new MovementResponse(
                movimiento.getId(),
                // getStock() es LAZY, pero solo se toca el id, que el proxy ya
                // conoce sin ir a la base.
                movimiento.getStock().getId(),
                movimiento.getCodigoReactivo(),
                movimiento.getNombreReactivo(),
                movimiento.getLote(),
                movimiento.getTipo(),
                movimiento.getCantidad(),
                movimiento.getCantidadAnterior(),
                movimiento.getCantidadResultante(),
                movimiento.getMotivo(),
                movimiento.getRegistradoPor(),
                movimiento.getCodigoMuestra(),
                movimiento.getFechaMovimiento());
    }
}
