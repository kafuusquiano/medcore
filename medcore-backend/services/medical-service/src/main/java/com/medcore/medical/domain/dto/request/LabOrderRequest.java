package com.medcore.medical.domain.dto.request;

import com.medcore.medical.domain.enums.LabOrderPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Orden de laboratorio a emitir dentro de una atencion.
 */
public record LabOrderRequest(

        LabOrderPriority prioridad,

        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
        String observaciones,

        @NotEmpty(message = "Debe solicitarse al menos una prueba")
        @Valid
        List<PruebaSolicitada> pruebas
) {

    /**
     * Prueba concreta del catalogo de laboratory-service.
     *
     * @param indicaciones preparacion previa, por ejemplo ayuno de 8 horas
     */
    public record PruebaSolicitada(

            @NotBlank(message = "El codigo de la prueba es obligatorio")
            @Size(max = 20, message = "El codigo no puede superar los 20 caracteres")
            String codigo,

            @NotBlank(message = "El nombre de la prueba es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String nombre,

            @Size(max = 300, message = "Las indicaciones no pueden superar los 300 caracteres")
            String indicaciones
    ) {
    }
}
