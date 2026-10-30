package com.medcore.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del servicio, de {@code infra/config-repo/analytics-service.yml}.
 *
 * <p>Se leen con {@code @ConfigurationProperties} y no con {@code @Value}
 * porque el config-server entrega las estructuras anidadas como claves
 * indexadas y {@code @Value} falla al resolverlas.
 *
 * <p>El umbral y la ventana viven en configuracion y no en el codigo porque
 * son el parametro que hay que afinar con el uso: si casi todas las alertas
 * se descartan, el umbral esta demasiado bajo, y corregirlo no deberia exigir
 * reconstruir la imagen.
 */
@ConfigurationProperties(prefix = "analytics")
public record AnalyticsProperties(
        Brote brote,
        String cronIndicadores
) {

    private static final int UMBRAL_POR_OMISION = 5;
    private static final int VENTANA_POR_OMISION = 7;

    /**
     * @param umbralCasos casos del mismo tipo en una zona para considerar que
     *                    hay brote
     * @param ventanaDias dias hacia atras que se miran
     */
    public record Brote(Integer umbralCasos, Integer ventanaDias) {
    }

    /**
     * Umbral efectivo, con valor por omision.
     *
     * <p>Sin este respaldo, un arranque con el config-server a medias dejaria
     * el umbral en null y la deteccion fallaria con NullPointerException en
     * lugar de vigilar. Un umbral de 1 convertiria cada caso anormal en un
     * brote, asi que tambien se descarta.
     */
    public int umbralCasos() {
        if (brote == null || brote.umbralCasos() == null || brote.umbralCasos() < 2) {
            return UMBRAL_POR_OMISION;
        }
        return brote.umbralCasos();
    }

    public int ventanaDias() {
        if (brote == null || brote.ventanaDias() == null || brote.ventanaDias() < 1) {
            return VENTANA_POR_OMISION;
        }
        return brote.ventanaDias();
    }
}
