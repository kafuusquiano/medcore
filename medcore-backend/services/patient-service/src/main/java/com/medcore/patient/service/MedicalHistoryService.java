package com.medcore.patient.service;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.patient.domain.dto.response.MedicalHistoryResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Linea de tiempo clinica del paciente.
 */
public interface MedicalHistoryService {

    PageableResponse<MedicalHistoryResponse> historialDe(Long idPaciente, Pageable pageable);

    /**
     * Registra un episodio. Es idempotente por (origen, referenciaExterna),
     * de modo que un reintento del servicio de origen no lo duplique.
     */
    MedicalHistoryResponse registrarEpisodio(Long idPaciente, String origen,
                                             String referenciaExterna, String titulo,
                                             String descripcion, String codigoCie10,
                                             Instant fechaEpisodio);
}
