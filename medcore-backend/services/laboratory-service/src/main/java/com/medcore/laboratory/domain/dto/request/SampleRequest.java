package com.medcore.laboratory.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de una muestra recibida en el laboratorio.
 *
 * <p>Solo pide el identificador de la orden y como llego el tubo. Los datos
 * del paciente no se envian: vienen del evento {@code lab-order.created} que
 * dejo la orden en la cola, y pedirlos de nuevo abriria la puerta a que la
 * muestra quede a nombre de otra persona.
 *
 * @param tipoMuestra lo que el tecnico tiene fisicamente delante; puede no
 *                    coincidir con lo que pedia la orden, y en ese caso el
 *                    servicio lo advierte pero no lo bloquea
 * @param urgente     si se marca, la muestra encabeza la cola de trabajo;
 *                    puede omitirse y entonces vale false
 */
public record SampleRequest(

        @NotNull(message = "Indique de que orden es la muestra")
        Long idOrden,

        @NotNull(message = "Indique el tipo de muestra")
        @Size(max = 30, message = "El tipo de muestra no puede pasar de 30 caracteres")
        String tipoMuestra,

        Boolean urgente,

        @Size(max = 120, message = "El nombre de quien tomo la muestra no puede pasar de 120 caracteres")
        String tomadaPor,

        @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
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
    public boolean esUrgente() {
        return Boolean.TRUE.equals(urgente);
    }
}
