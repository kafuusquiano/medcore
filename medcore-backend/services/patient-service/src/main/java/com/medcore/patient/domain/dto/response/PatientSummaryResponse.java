package com.medcore.patient.domain.dto.response;

import com.medcore.patient.domain.enums.PatientStatus;

/**
 * Vista reducida para listados y selectores.
 *
 * <p>Existe para que una busqueda que devuelve cien pacientes no transporte
 * direcciones, alergias ni contactos de emergencia, que son datos sensibles
 * y no se muestran en una tabla de resultados.
 */
public record PatientSummaryResponse(
        Long id,
        String dni,
        String nombreCompleto,
        int edad,
        String zona,
        PatientStatus estado
) {
}
