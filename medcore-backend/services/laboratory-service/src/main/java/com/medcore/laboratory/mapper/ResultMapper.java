package com.medcore.laboratory.mapper;

import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.domain.entity.Result;
import org.springframework.stereotype.Component;

/** Conversion de un resultado a su vista. */
@Component
public class ResultMapper {

    public ResultResponse aRespuesta(Result resultado) {
        return new ResultResponse(
                resultado.getId(),
                resultado.getMuestra().getId(),
                resultado.getMuestra().getCodigo(),
                resultado.getCodigoPrueba(),
                resultado.getNombrePrueba(),
                resultado.getValorNumerico(),
                resultado.getValorTexto(),
                resultado.getUnidad(),
                resultado.getRangoReferencia(),
                resultado.getInterpretacion(),
                resultado.getInterpretacion().etiqueta(),
                resultado.isCritico(),
                resultado.getOrigen(),
                resultado.getRegistradoPor(),
                // Para que el informe senale que este valor rectifica otro.
                resultado.getIdResultadoCorregido() != null,
                resultado.getIdResultadoCorregido(),
                resultado.getObservaciones(),
                resultado.getFechaResultado());
    }
}
