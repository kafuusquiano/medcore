package com.medcore.medical.service;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.request.AttentionRequest;
import com.medcore.medical.domain.dto.request.AttentionUpdateRequest;
import com.medcore.medical.domain.dto.request.DiagnosisRequest;
import com.medcore.medical.domain.dto.response.AttentionResponse;
import org.springframework.data.domain.Pageable;

/**
 * Atenciones medicas.
 *
 * <p>El ciclo es: abrir, registrar hallazgos, anadir diagnosticos y
 * finalizar. Una vez finalizada no admite cambios: es un registro clinico.
 */
public interface AttentionService {

    AttentionResponse abrir(AttentionRequest request);

    AttentionResponse registrarHallazgos(Long id, AttentionUpdateRequest request);

    AttentionResponse agregarDiagnostico(Long id, DiagnosisRequest request);

    /** Exige al menos un diagnostico. */
    AttentionResponse finalizar(Long id);

    AttentionResponse anular(Long id, String motivo);

    AttentionResponse obtener(Long id);

    PageableResponse<AttentionResponse> atencionesDePaciente(Long idPaciente, Pageable pageable);

    PageableResponse<AttentionResponse> atencionesDeMedico(Long idMedico, Pageable pageable);
}
