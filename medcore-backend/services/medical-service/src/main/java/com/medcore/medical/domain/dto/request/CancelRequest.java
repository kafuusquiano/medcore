package com.medcore.medical.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo de una anulacion.
 *
 * <p>Obligatorio: las restricciones chk_attention_anulacion y
 * chk_order_anulacion exigen que todo registro anulado diga por que.
 */
public record CancelRequest(

        @NotBlank(message = "El motivo de la anulacion es obligatorio")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
