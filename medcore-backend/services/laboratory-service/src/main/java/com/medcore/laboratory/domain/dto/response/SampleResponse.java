package com.medcore.laboratory.domain.dto.response;

import com.medcore.laboratory.domain.enums.SampleStatus;

import java.time.Instant;
import java.util.List;

/**
 * Vista de una muestra.
 *
 * @param admiteResultados  calculado, para que el frontend sepa si mostrar el
 *                          formulario de entrada de resultados sin tener que
 *                          replicar la maquina de estados
 * @param resultados        los resultados ya registrados; vacio mientras el
 *                          tubo espera turno
 */
public record SampleResponse(
        Long id,
        String codigo,
        Long idOrden,
        String codigoOrden,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,
        String sexoPaciente,
        Integer edadPaciente,
        Long idMedico,
        String tipoMuestra,
        SampleStatus estado,
        boolean urgente,
        boolean admiteResultados,
        String observaciones,
        String motivoRechazo,
        String tomadaPor,
        Instant fechaToma,
        Instant fechaProceso,
        List<ResultResponse> resultados
) {
}
