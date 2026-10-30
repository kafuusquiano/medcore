package com.medcore.medical.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Diagnostico a registrar en una atencion.
 *
 * <p>Solo se envia el codigo: la descripcion y la marca de notificable se
 * toman del catalogo CIE-10, para que no dependan de lo que escriba el
 * cliente y no se pueda emitir un diagnostico con una descripcion inventada.
 *
 * @param principal true para el diagnostico que motiva la atencion
 * @param casoNuevo false si es el seguimiento de un caso previo
 */
public record DiagnosisRequest(

        @NotBlank(message = "El codigo CIE-10 es obligatorio")
        @Pattern(regexp = "^[A-Z][0-9]{2}([.][0-9X])?$",
                message = "El codigo CIE-10 no tiene un formato valido, por ejemplo A09 o J45.0")
        String codigoCie10,

        Boolean principal,

        Boolean casoNuevo,

        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
        String observaciones
) {

    /**
     * Un indicador ausente en el JSON vale lo mismo que false.
     *
     * <p>El campo se declara como Boolean y no como boolean porque Jackson 3,
     * el de Spring Boot 4, rechaza con 400 un cuerpo que omite un primitivo
     * en lugar de dejarlo en false, que era el comportamiento de Jackson 2.
     * Un formulario que no envia la casilla sin marcar es lo normal, y
     * obligar al cliente a mandarla explicitamente seria una trampa.
     */
    public boolean esPrincipal() {
        return Boolean.TRUE.equals(principal);
    }

    public boolean esCasoNuevo() {
        return Boolean.TRUE.equals(casoNuevo);
    }
}
