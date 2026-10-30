package com.medcore.analytics.mapper;

import com.medcore.analytics.domain.document.OutbreakAlert;
import com.medcore.analytics.domain.dto.response.AlertResponse;
import org.springframework.stereotype.Component;

/** Conversion de una alerta de brote a su vista. */
@Component
public class AnalyticsMapper {

    public AlertResponse aRespuesta(OutbreakAlert alerta) {
        return new AlertResponse(
                alerta.id(),
                alerta.zona(),
                alerta.codigoPrueba(),
                alerta.nombrePrueba(),
                alerta.casos(),
                alerta.casosCriticos(),
                alerta.umbral(),
                alerta.exceso(),
                alerta.ventanaDias(),
                alerta.nivelRiesgo(),
                alerta.nivelRiesgo().etiqueta(),
                alerta.estado(),
                alerta.estado().etiqueta(),
                // Calculado, para que el panel no repita la regla al decidir
                // si destaca la alerta.
                alerta.nivelRiesgo().exigeAvisoInmediato(),
                alerta.dnisAfectados(),
                alerta.primerCaso(),
                alerta.ultimoCaso(),
                alerta.resueltaPor(),
                alerta.motivoResolucion(),
                alerta.fechaResolucion(),
                alerta.fechaDeteccion(),
                alerta.fechaActualizacion());
    }
}
