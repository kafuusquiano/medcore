package com.medcore.inventory.service;

import com.medcore.inventory.domain.dto.response.ReagentResponse;
import com.medcore.inventory.domain.dto.response.StockResponse;

import java.util.List;

/**
 * Avisos del almacen.
 *
 * <p>Se separa de StockService porque no consulta ni modifica stock: decide
 * que merece aviso y lo publica. El scheduler la llama cada madrugada y el
 * responsable del almacen puede pedir el mismo repaso a demanda.
 */
public interface AlertService {

    /**
     * Revisa todo el almacen y publica lo que merece aviso.
     *
     * <p>Publica {@code stock.low} por cada reactivo bajo minimo y
     * {@code stock.expired} por cada lote vencido o por vencer.
     *
     * @return cuantos avisos se publicaron de cada clase
     */
    Resumen revisar(String requestId);

    /** Reactivos bajo minimo, sin publicar nada. */
    List<ReagentResponse> reactivosBajoMinimo();

    /** Lotes vencidos y por vencer, sin publicar nada. */
    List<StockResponse> lotesQueRequierenAtencion();

    /**
     * @param stockBajo  reactivos cuyo total quedo por debajo del minimo
     * @param vencidos   lotes que ya vencieron y siguen en la estanteria
     * @param porVencer  lotes que vencen dentro del plazo de aviso
     */
    record Resumen(int stockBajo, int vencidos, int porVencer) {

        public int total() {
            return stockBajo + vencidos + porVencer;
        }
    }
}
