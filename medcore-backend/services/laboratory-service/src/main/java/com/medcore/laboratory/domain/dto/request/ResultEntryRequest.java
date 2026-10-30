package com.medcore.laboratory.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada manual de un resultado.
 *
 * <p>Se usa cuando la prueba no la emite un equipo conectado: una lectura al
 * microscopio, un cultivo que se informa en texto, o un analizador que esta
 * fuera de linea y hay que teclear lo que marco.
 *
 * <p>Un resultado puede ser numerico o de texto, y por eso ninguno de los dos
 * campos es obligatorio por anotacion: la comprobacion de que llegue al menos
 * uno, y de que las pruebas cuantitativas traigan numero, la hace
 * ResultService, que es quien conoce el catalogo.
 *
 * @param valorNumerico valor medido en las pruebas cuantitativas
 * @param valorTexto    informe de las cualitativas, por ejemplo el resultado
 *                      de un cultivo
 */
public record ResultEntryRequest(

        @NotBlank(message = "Indique el codigo de la prueba")
        @Size(max = 20, message = "El codigo de prueba no puede pasar de 20 caracteres")
        String codigoPrueba,

        Double valorNumerico,

        @Size(max = 500, message = "El valor de texto no puede pasar de 500 caracteres")
        String valorTexto,

        @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
        String observaciones
) {

    public boolean tieneAlgunValor() {
        return valorNumerico != null || (valorTexto != null && !valorTexto.isBlank());
    }
}
