package com.medcore.inventory.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta o modificacion de un reactivo del catalogo.
 *
 * <p>No lleva cantidades: el material fisico entra como lote, con su fecha de
 * vencimiento, por la API de stock. Permitir una cantidad aqui crearia
 * existencias sin lote ni vencimiento, que es justamente lo que el inventario
 * de un laboratorio no puede tener.
 *
 * @param stockMinimo cantidad por debajo de la cual se avisa para reponer
 * @param stockOptimo referencia para quien hace el pedido; puede omitirse
 */
public record ReagentRequest(

        @NotBlank(message = "Indique el codigo del reactivo")
        @Size(max = 30, message = "El codigo no puede pasar de 30 caracteres")
        @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]*$",
                message = "El codigo solo admite mayusculas, numeros y guiones")
        String codigo,

        @NotBlank(message = "Indique el nombre del reactivo")
        @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres")
        String nombre,

        @NotBlank(message = "Indique la categoria")
        @Size(max = 60, message = "La categoria no puede pasar de 60 caracteres")
        String categoria,

        @NotBlank(message = "Indique la unidad de medida")
        @Size(max = 30, message = "La unidad de medida no puede pasar de 30 caracteres")
        String unidadMedida,

        @Size(max = 120, message = "El proveedor no puede pasar de 120 caracteres")
        String proveedor,

        @Min(value = 0, message = "El stock minimo no puede ser negativo")
        Integer stockMinimo,

        @Min(value = 0, message = "El stock optimo no puede ser negativo")
        Integer stockOptimo,

        /**
         * Envoltorio y no primitivo: Jackson 3 rechaza con 400 un cuerpo que
         * omita un primitivo (regla MC8 del README).
         */
        Boolean requiereRefrigeracion,

        @Size(max = 300, message = "Las observaciones no pueden pasar de 300 caracteres")
        String observaciones
) {

    public boolean exigeRefrigeracion() {
        return Boolean.TRUE.equals(requiereRefrigeracion);
    }

    public int minimoOCero() {
        return stockMinimo == null ? 0 : stockMinimo;
    }
}
