package com.medcore.commons.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Envoltura estandar de toda respuesta REST de MedCore (regla G2).
 *
 * <p>Los campos nulos no se serializan, de modo que una respuesta de exito no
 * incluye {@code error} y una de fallo no incluye {@code data}.
 *
 * @param success   indica si la operacion se completo correctamente
 * @param message   mensaje en espanol dirigido al usuario final
 * @param data      carga util; nula en las respuestas de error
 * @param error     detalle del error; nulo en las respuestas de exito
 * @param timestamp instante en que se construyo la respuesta, en UTC
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ErrorResponse error,
        Instant timestamp
) {

    /** Respuesta correcta con mensaje y carga util. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    /** Respuesta correcta sin carga util, por ejemplo tras un borrado. */
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now());
    }

    /** Respuesta de error. El detalle tecnico viaja dentro de {@code error}. */
    public static <T> ApiResponse<T> fail(String message, ErrorResponse error) {
        return new ApiResponse<>(false, message, null, error, Instant.now());
    }
}
