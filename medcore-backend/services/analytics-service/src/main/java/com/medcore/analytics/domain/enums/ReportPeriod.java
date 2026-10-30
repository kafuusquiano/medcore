package com.medcore.analytics.domain.enums;

import java.time.LocalDate;

/**
 * Ventana de un informe o de una serie temporal.
 *
 * <p>Cada valor sabe calcular su fecha de inicio, de modo que el calculo no
 * se repita en cada servicio que acepte un periodo.
 */
public enum ReportPeriod {

    DIARIO(1),
    SEMANAL(7),
    MENSUAL(30),
    TRIMESTRAL(90),
    ANUAL(365);

    private final int dias;

    ReportPeriod(int dias) {
        this.dias = dias;
    }

    public int dias() {
        return dias;
    }

    public LocalDate desde(LocalDate hasta) {
        return hasta.minusDays(dias - 1L);
    }

    public LocalDate desdeHoy() {
        return desde(LocalDate.now());
    }

    /**
     * Cada cuantos dias agrupar los puntos de una serie.
     *
     * <p>Una serie anual con un punto por dia son 365 puntos, que en una
     * grafica no se leen. Se agrupa por semana o por mes segun la ventana,
     * para que el numero de puntos quede siempre manejable.
     */
    public int diasPorPunto() {
        return switch (this) {
            case DIARIO, SEMANAL, MENSUAL -> 1;
            case TRIMESTRAL -> 7;
            case ANUAL -> 30;
        };
    }

    public String etiqueta() {
        return switch (this) {
            case DIARIO -> "Ultimo dia";
            case SEMANAL -> "Ultima semana";
            case MENSUAL -> "Ultimo mes";
            case TRIMESTRAL -> "Ultimo trimestre";
            case ANUAL -> "Ultimo anio";
        };
    }
}
