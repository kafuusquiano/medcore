package com.medcore.analytics.domain.enums;

/**
 * Gravedad de una alerta de brote.
 *
 * <p>Sale de cuanto se supera el umbral y de cuantos casos son criticos. No se
 * guarda a mano: lo decide {@link #evaluar} para que dos alertas con las
 * mismas cifras no acaben con niveles distintos segun quien las abrio.
 */
public enum RiskLevel {

    /** En el umbral o apenas por encima. Conviene vigilar. */
    BAJO,

    /** Al menos el doble del umbral, o algun caso critico. */
    MEDIO,

    /** Al menos el triple del umbral, o varios casos criticos. */
    ALTO;

    /**
     * Evalua el nivel.
     *
     * <p>Un solo caso critico ya sube a MEDIO aunque los casos apenas superen
     * el umbral: un valor critico significa que hay alguien en riesgo
     * inmediato, y eso pesa mas que el recuento.
     */
    public static RiskLevel evaluar(int casos, int umbral, int casosCriticos) {
        int base = umbral <= 0 ? 1 : umbral;

        if (casos >= base * 3 || casosCriticos >= 3) {
            return ALTO;
        }
        if (casos >= base * 2 || casosCriticos >= 1) {
            return MEDIO;
        }
        return BAJO;
    }

    /** Texto para mostrar; se define aqui para que el panel y el informe coincidan. */
    public String etiqueta() {
        return switch (this) {
            case BAJO -> "Riesgo bajo";
            case MEDIO -> "Riesgo medio";
            case ALTO -> "Riesgo alto";
        };
    }

    /** Exige aviso inmediato al responsable de epidemiologia. */
    public boolean exigeAvisoInmediato() {
        return this == ALTO;
    }
}
