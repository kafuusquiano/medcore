package com.medcore.laboratory.domain.dto.response;

import com.medcore.laboratory.domain.enums.ResultInterpretation;

import java.time.Instant;

/**
 * Vista de un resultado.
 *
 * @param rangoReferencia       el que se aplico al emitir, copiado en el
 *                              resultado; los del catalogo pueden cambiar
 *                              despues y el informe debe conservar contra que
 *                              se interpreto
 * @param etiquetaInterpretacion texto para mostrar, por ejemplo "Alto"
 * @param esCorreccion          indica que este resultado rectifica otro
 *                              anterior, para que el informe lo senale
 */
public record ResultResponse(
        Long id,
        Long idMuestra,
        String codigoMuestra,
        String codigoPrueba,
        String nombrePrueba,
        Double valorNumerico,
        String valorTexto,
        String unidad,
        String rangoReferencia,
        ResultInterpretation interpretacion,
        String etiquetaInterpretacion,
        boolean critico,
        String origen,
        String registradoPor,
        boolean esCorreccion,
        Long idResultadoCorregido,
        String observaciones,
        Instant fechaResultado
) {
}
