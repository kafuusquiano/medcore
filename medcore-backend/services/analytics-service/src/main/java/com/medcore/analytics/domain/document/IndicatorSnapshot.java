package com.medcore.analytics.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Foto de un indicador en un momento dado.
 *
 * <p>Existe porque un indicador calculado sobre los registros vivos cambia
 * cada vez que se consulta: si un resultado se corrige la semana siguiente,
 * el porcentaje de positividad del mes pasado deja de ser el que se informo.
 * La foto conserva el valor con el que se tomo una decision.
 *
 * <p>Las consultas en vivo siguen existiendo, en EpidemiologicalService y
 * TrendAnalysisService, y son las que el panel usa a diario. Esto es para los
 * informes que se archivan o se envian.
 *
 * @param indicador  nombre del indicador, por ejemplo
 *                   {@code positividad_por_zona}
 * @param desglose   valores por clave, por ejemplo cada zona con su cifra
 */
@Document(collection = "indicator_snapshots")
public record IndicatorSnapshot(

        @Id String id,

        String indicador,

        /** Fecha a la que corresponde la foto, no cuando se calculo. */
        LocalDate periodo,

        String descripcion,
        Double valor,
        String unidad,

        Map<String, Double> desglose,

        /** Cuantos registros entraron en el calculo. */
        long registrosConsiderados,

        Instant fechaCalculo
) {

    public static IndicatorSnapshot de(String indicador, LocalDate periodo, String descripcion,
                                       Double valor, String unidad,
                                       Map<String, Double> desglose,
                                       long registrosConsiderados) {
        return new IndicatorSnapshot(
                null, indicador, periodo, descripcion, valor, unidad,
                desglose == null ? Map.of() : Map.copyOf(desglose),
                registrosConsiderados, Instant.now());
    }

    /**
     * Una foto sin registros detras no dice "cero": dice que no habia datos.
     * El panel debe distinguirlo para no pintar una caida que no ocurrio.
     */
    public boolean tieneDatos() {
        return registrosConsiderados > 0;
    }
}
