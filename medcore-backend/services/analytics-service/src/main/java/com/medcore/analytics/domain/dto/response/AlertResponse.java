package com.medcore.analytics.domain.dto.response;

import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.RiskLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Vista de una alerta de brote.
 *
 * @param dnisAfectados documentos de los pacientes. Se devuelven porque el
 *                      responsable de epidemiologia necesita poder rastrear a
 *                      quien afecta el brote; la ruta exige rol, y en el
 *                      documento no se guarda el nombre.
 * @param exceso        casos por encima del umbral, para ordenar por gravedad
 */
public record AlertResponse(
        String id,
        String zona,
        String codigoPrueba,
        String nombrePrueba,

        int casos,
        int casosCriticos,
        int umbral,
        int exceso,
        int ventanaDias,

        RiskLevel nivelRiesgo,
        String etiquetaRiesgo,
        AlertStatus estado,
        String etiquetaEstado,
        boolean exigeAvisoInmediato,

        List<String> dnisAfectados,

        LocalDate primerCaso,
        LocalDate ultimoCaso,

        String resueltaPor,
        String motivoResolucion,
        Instant fechaResolucion,

        Instant fechaDeteccion,
        Instant fechaActualizacion
) {
}
