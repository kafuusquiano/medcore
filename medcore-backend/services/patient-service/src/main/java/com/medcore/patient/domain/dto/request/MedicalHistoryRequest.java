package com.medcore.patient.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Alta de un episodio en el historial clinico.
 *
 * @param origen            servicio que lo genera, por ejemplo medical-service
 * @param referenciaExterna identificador del registro en ese servicio; es lo
 *                          que hace idempotente el alta ante un reintento
 * @param fechaEpisodio     cuando ocurrio; si es nula se toma el instante actual
 */
public record MedicalHistoryRequest(

        @NotBlank(message = "El origen es obligatorio")
        @Size(max = 40, message = "El origen no puede superar los 40 caracteres")
        String origen,

        @Size(max = 60, message = "La referencia externa no puede superar los 60 caracteres")
        String referenciaExterna,

        @NotBlank(message = "El titulo es obligatorio")
        @Size(max = 150, message = "El titulo no puede superar los 150 caracteres")
        String titulo,

        @Size(max = 1000, message = "La descripcion no puede superar los 1000 caracteres")
        String descripcion,

        @Pattern(regexp = "^$|^[A-Z][0-9]{2}(\\.[0-9X])?$",
                message = "El codigo CIE-10 no tiene un formato valido, por ejemplo A09 o J45.0")
        String codigoCie10,

        @PastOrPresent(message = "La fecha del episodio no puede estar en el futuro")
        Instant fechaEpisodio
) {
}
