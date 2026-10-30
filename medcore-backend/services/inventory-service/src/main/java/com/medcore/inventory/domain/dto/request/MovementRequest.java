package com.medcore.inventory.domain.dto.request;

import com.medcore.inventory.domain.enums.MovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Movimiento sobre un lote ya registrado.
 *
 * <p>La cantidad se envia siempre en positivo salvo en un AJUSTE: el signo lo
 * determina el tipo de movimiento, de modo que quien llama no tiene que
 * recordar si una merma resta. El AJUSTE es la excepcion porque corrige en
 * cualquier direccion tras un recuento fisico.
 *
 * @param motivo        obligatorio en MERMA, AJUSTE y DEVOLUCION. Una salida
 *                      por consumo no lo exige: su motivo es el trabajo
 *                      diario, y se identifica con el codigo de la muestra.
 * @param codigoMuestra muestra que consumio el material, si el movimiento
 *                      viene del laboratorio
 */
public record MovementRequest(

        @NotNull(message = "Indique el tipo de movimiento")
        MovementType tipo,

        @NotNull(message = "Indique la cantidad")
        Integer cantidad,

        @Size(max = 300, message = "El motivo no puede pasar de 300 caracteres")
        String motivo,

        @Size(max = 25, message = "El codigo de muestra no puede pasar de 25 caracteres")
        String codigoMuestra
) {

    public boolean tieneMotivo() {
        return motivo != null && !motivo.isBlank();
    }
}
