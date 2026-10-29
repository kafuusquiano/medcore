package com.medcore.medical.domain.dto.response;

import com.medcore.medical.domain.enums.AttentionStatus;

import java.time.Instant;
import java.util.List;

/**
 * Atencion medica completa.
 *
 * @param imc indice de masa corporal, calculado al responder; nulo si falta
 *            peso o talla
 */
public record AttentionResponse(
        Long id,
        Long idCita,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,
        Long idMedico,
        String nombreMedico,
        String motivoConsulta,
        String anamnesis,
        String examenFisico,
        String tratamiento,
        String observaciones,
        Double temperatura,
        Integer presionSistolica,
        Integer presionDiastolica,
        Integer frecuenciaCardiaca,
        Double pesoKg,
        Double tallaCm,
        Double imc,
        AttentionStatus estado,
        String motivoAnulacion,
        List<DiagnosisResponse> diagnosticos,
        Instant fechaAtencion,
        Instant fechaCierre
) {
}
