package com.medcore.medical.domain.dto.response;

import java.time.Instant;

/** Diagnostico registrado en una atencion. */
public record DiagnosisResponse(
        Long id,
        String codigoCie10,
        String descripcionCie10,
        boolean principal,
        boolean casoNuevo,
        boolean notificable,
        String observaciones,
        Instant fechaRegistro
) {
}
