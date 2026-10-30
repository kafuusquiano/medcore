package com.medcore.inventory.domain.dto.response;

import com.medcore.inventory.domain.enums.MovementType;

import java.time.Instant;

/**
 * Vista de un movimiento del historial.
 *
 * <p>Lleva la cantidad antes y despues, no solo el delta: con eso un recuento
 * que no cuadra se rastrea hasta el movimiento exacto en que la cuenta se
 * desvio, sin sumar toda la cadena.
 *
 * @param cantidad  delta con signo, tal como se guardo: negativo en salidas,
 *                  mermas y devoluciones
 */
public record MovementResponse(
        Long id,
        Long idStock,
        String codigoReactivo,
        String nombreReactivo,
        String lote,
        MovementType tipo,
        Integer cantidad,
        Integer cantidadAnterior,
        Integer cantidadResultante,
        String motivo,
        String registradoPor,
        String codigoMuestra,
        Instant fechaMovimiento
) {
}
