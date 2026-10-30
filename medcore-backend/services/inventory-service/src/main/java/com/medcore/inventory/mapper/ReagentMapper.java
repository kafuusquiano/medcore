package com.medcore.inventory.mapper;

import com.medcore.inventory.domain.dto.response.ReagentResponse;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversion de un reactivo a su vista. */
@Component
public class ReagentMapper {

    private final StockMapper stockMapper;

    public ReagentMapper(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    /**
     * Vista del reactivo sin sus lotes, para los listados.
     *
     * <p>El total disponible se recibe calculado: lo suma el repositorio con
     * una consulta que descarta lo vencido y lo retirado. Hacerlo aqui
     * obligaria a cargar todos los lotes de cada reactivo del listado.
     */
    public ReagentResponse aRespuesta(Reagent reactivo, int totalDisponible) {
        return aRespuesta(reactivo, totalDisponible, List.of(), 0);
    }

    public ReagentResponse aRespuesta(Reagent reactivo, int totalDisponible,
                                      List<Stock> lotes, int diasAvisoVencimiento) {
        return new ReagentResponse(
                reactivo.getId(),
                reactivo.getCodigo(),
                reactivo.getNombre(),
                reactivo.getCategoria(),
                reactivo.getUnidadMedida(),
                reactivo.getProveedor(),
                reactivo.getStockMinimo(),
                reactivo.getStockOptimo(),
                totalDisponible,
                // Calculados, para que el frontend no repita las reglas.
                reactivo.estaBajoMinimo(totalDisponible),
                reactivo.cantidadSugeridaDePedido(totalDisponible),
                reactivo.isRequiereRefrigeracion(),
                reactivo.isActivo(),
                reactivo.getObservaciones(),
                lotes.stream()
                        .map(lote -> stockMapper.aRespuesta(lote, diasAvisoVencimiento))
                        .toList(),
                reactivo.getFechaCreacion(),
                reactivo.getFechaActualizacion());
    }
}
