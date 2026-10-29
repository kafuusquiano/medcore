package com.medcore.appointment.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo de la cancelacion.
 *
 * <p>Es obligatorio: la restriccion chk_appointment_cancelacion de la base
 * exige que toda cita anulada diga por que, y sin motivo quedaria un hueco en
 * la agenda sin explicacion.
 */
public record CancelRequest(

        @NotBlank(message = "El motivo de la cancelacion es obligatorio")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
