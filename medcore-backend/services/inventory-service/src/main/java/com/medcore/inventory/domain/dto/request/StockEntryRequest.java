package com.medcore.inventory.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Entrada de un lote al almacen.
 *
 * <p>El lote y el vencimiento se piden juntos porque es asi como llega el
 * material: una caja con su numero de lote impreso y su fecha de caducidad.
 * Registrar cantidad sin lote dejaria existencias que no se pueden rastrear
 * cuando el proveedor avisa de un defecto.
 *
 * @param fechaVencimiento puede omitirse en los insumos que no caducan, como
 *                         gradillas o puntas de pipeta
 * @param fechaIngreso     si se omite, se toma la fecha de hoy; se permite
 *                         indicarla para registrar material recibido dias
 *                         antes
 */
public record StockEntryRequest(

        @NotNull(message = "Indique de que reactivo es el lote")
        Long idReactivo,

        @NotBlank(message = "Indique el numero de lote")
        @Size(max = 60, message = "El lote no puede pasar de 60 caracteres")
        String lote,

        @NotNull(message = "Indique la cantidad que ingresa")
        @Min(value = 1, message = "La cantidad de ingreso debe ser al menos 1")
        Integer cantidad,

        LocalDate fechaVencimiento,

        LocalDate fechaIngreso,

        @Size(max = 80, message = "La ubicacion no puede pasar de 80 caracteres")
        String ubicacion,

        @Size(max = 300, message = "Las observaciones no pueden pasar de 300 caracteres")
        String observaciones
) {

    public LocalDate ingresoOHoy() {
        return fechaIngreso == null ? LocalDate.now() : fechaIngreso;
    }
}
