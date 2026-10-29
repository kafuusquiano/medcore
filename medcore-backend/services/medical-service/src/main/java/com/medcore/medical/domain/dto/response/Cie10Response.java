package com.medcore.medical.domain.dto.response;

/** Codigo del catalogo CIE-10. */
public record Cie10Response(
        String codigo,
        String descripcion,
        String capitulo,
        boolean notificable
) {
}
