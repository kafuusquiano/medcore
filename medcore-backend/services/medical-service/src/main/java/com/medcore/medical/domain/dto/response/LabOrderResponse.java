package com.medcore.medical.domain.dto.response;

import com.medcore.medical.domain.enums.LabOrderPriority;
import com.medcore.medical.domain.enums.LabOrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * Orden de laboratorio.
 *
 * @param codigo       codigo legible que se imprime y lleva el paciente
 * @param puedeAnularse indica si el laboratorio aun no ha comenzado
 */
public record LabOrderResponse(
        Long id,
        String codigo,
        Long idAtencion,
        Long idPaciente,
        Long idMedico,
        LabOrderPriority prioridad,
        LabOrderStatus estado,
        String observaciones,
        String motivoAnulacion,
        boolean puedeAnularse,
        List<PruebaResponse> pruebas,
        Instant fechaEmision
) {

    public record PruebaResponse(
            Long id,
            String codigo,
            String nombre,
            String indicaciones
    ) {
    }
}
