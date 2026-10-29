package com.medcore.commons.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Detalle de un error devuelto dentro de {@link ApiResponse}.
 *
 * <p>Nunca transporta stack traces: la regla G3 prohibe exponerlos al cliente.
 *
 * @param codigo       identificador estable del error, por ejemplo PACIENTE_NO_ENCONTRADO
 * @param estadoHttp   codigo HTTP asociado
 * @param ruta         URI de la peticion que fallo
 * @param requestId    valor de X-Request-Id propagado por el gateway, para rastrear en los logs
 * @param validaciones errores de validacion por campo; nulo si no aplica
 * @param detalles     informacion adicional del dominio; nula si no aplica
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String codigo,
        int estadoHttp,
        String ruta,
        String requestId,
        List<FieldError> validaciones,
        Map<String, Object> detalles
) {

    /**
     * Error de validacion asociado a un campo concreto del cuerpo de la peticion.
     *
     * @param campo   nombre del campo que no cumple la restriccion
     * @param mensaje descripcion en espanol de la restriccion incumplida
     */
    public record FieldError(String campo, String mensaje) {
    }

    public static ErrorResponse of(String codigo, int estadoHttp, String ruta, String requestId) {
        return new ErrorResponse(codigo, estadoHttp, ruta, requestId, null, null);
    }

    public static ErrorResponse ofValidacion(String codigo, int estadoHttp, String ruta,
                                             String requestId, List<FieldError> validaciones) {
        return new ErrorResponse(codigo, estadoHttp, ruta, requestId, validaciones, null);
    }
}
