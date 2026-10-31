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
    /**
     * Cierra la atencion y publica sus diagnosticos.
     *
     * <p>Es aqui y no al anadir cada diagnostico donde se publica
     * diagnosis.registered: uno de una atencion abierta todavia puede cambiar,
     * y un caso retirado no se puede descontar de un informe que ya salio.
     *
     * @param requestId el de la peticion, para poder seguir el rastro de un
     *                  caso desde la consulta hasta el informe epidemiologico
     */
    AttentionResponse finalizar(Long id, String requestId);

    AttentionResponse anular(Long id, String motivo);

    AttentionResponse obtener(Long id);

    PageableResponse<AttentionResponse> atencionesDePaciente(Long idPaciente, Pageable pageable);

    PageableResponse<AttentionResponse> atencionesDeMedico(Long idMedico, Pageable pageable);
}
