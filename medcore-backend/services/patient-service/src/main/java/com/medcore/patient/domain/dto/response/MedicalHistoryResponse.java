package com.medcore.patient.domain.dto.response;

import java.time.Instant;

/**
 * Episodio del historial clinico.
 *
 * @param origen            servicio que lo genero
 * @param referenciaExterna identificador del registro en ese servicio
 */
public record MedicalHistoryResponse(
        Long id,
        Long idPaciente,
        String origen,
        String referenciaExterna,
        String titulo,
        String descripcion,
        String codigoCie10,
        Instant fechaEpisodio,
        Instant fechaRegistro
) {
}
