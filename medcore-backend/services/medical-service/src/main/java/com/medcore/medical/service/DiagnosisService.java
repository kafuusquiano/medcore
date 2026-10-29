package com.medcore.medical.service;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.response.Cie10Response;
import com.medcore.medical.domain.dto.response.DiagnosisResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Catalogo CIE-10 y consultas sobre los diagnosticos emitidos.
 */
public interface DiagnosisService {

    /** Busca en el catalogo por codigo o por texto de la descripcion. */
    PageableResponse<Cie10Response> buscarCie10(String texto, Pageable pageable);

    Cie10Response obtenerCie10(String codigo);

    /** Casos de notificacion obligatoria en un intervalo, para la DIRESA. */
    List<DiagnosisResponse> notificablesEntre(LocalDate desde, LocalDate hasta);

    /** Conteo de diagnosticos por codigo, base de los indicadores. */
    List<ConteoDiagnostico> conteoPorCodigo(LocalDate desde, LocalDate hasta);

    /**
     * @param codigo      codigo CIE-10
     * @param descripcion descripcion del catalogo
     * @param casos       numero de diagnosticos emitidos en el intervalo
     */
    record ConteoDiagnostico(String codigo, String descripcion, long casos) {
    }
}
