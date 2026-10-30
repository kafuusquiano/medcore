package com.medcore.analytics.service;

import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.domain.dto.response.HeatmapPointResponse;
import com.medcore.commons.event.ResultEvent;

import java.time.LocalDate;
import java.util.List;

/** Registro de casos y vista geografica. */
public interface EpidemiologicalService {

    /**
     * Registra el resultado recibido como caso epidemiologico.
     *
     * <p>Es idempotente: si el resultado ya estaba registrado no hace nada.
     * Kafka puede entregar el mismo evento dos veces, y un caso contado doble
     * falsea la vigilancia entera.
     *
     * @return el registro, nuevo o el que ya existia
     */
    AnalyticsRecord registrar(ResultEvent evento);

    /**
     * Mapa de calor: casos anormales por zona en una ventana.
     *
     * <p>La intensidad se calcula en el servidor, relativa a la zona con mas
     * casos del mismo periodo, para que todos los clientes pinten la misma
     * escala. Si cada panel la normalizara por su cuenta, dos pantallas del
     * mismo dato mostrarian colores distintos.
     */
    List<HeatmapPointResponse> mapaDeCalor(LocalDate desde, LocalDate hasta);

    /** Casos de una zona en una ventana, para abrir el detalle del mapa. */
    List<AnalyticsRecord> casosDeZona(String zona, LocalDate desde, LocalDate hasta);

    /** Valores criticos de una ventana, los que exigieron aviso inmediato. */
    List<AnalyticsRecord> criticos(LocalDate desde, LocalDate hasta);

    /** Zonas con registros, para ofrecer el filtro del panel. */
    List<String> zonas();
}
