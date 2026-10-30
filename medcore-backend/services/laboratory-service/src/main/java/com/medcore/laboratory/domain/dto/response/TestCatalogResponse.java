package com.medcore.laboratory.domain.dto.response;

import java.util.List;

/**
 * Prueba del catalogo con sus rangos y sus limites criticos.
 *
 * <p>Se devuelve todo junto porque es lo que el medico necesita para decidir
 * si pide la prueba: que mide, en que muestra, cuanto tarda y contra que se
 * va a comparar el resultado.
 */
public record TestCatalogResponse(
        String codigo,
        String nombre,
        String area,
        String unidad,
        String tipoMuestra,
        boolean cuantitativa,
        String indicaciones,
        Integer tiempoEntregaHoras,
        boolean activa,
        List<RangoResponse> rangos,
        LimitesCriticosResponse limitesCriticos
) {

    /**
     * Rango de normalidad para un grupo de pacientes.
     *
     * @param sexo     nulo cuando el rango vale para cualquier sexo
     * @param criterio texto ya armado, por ejemplo "Varones de 15 anos en
     *                 adelante", para que el frontend no tenga que componerlo
     */
    public record RangoResponse(
            String sexo,
            Integer edadMinAnios,
            Integer edadMaxAnios,
            Double valorMin,
            Double valorMax,
            String criterio
    ) {
    }

    /**
     * @param limiteInferior nulo en las pruebas criticas solo por arriba, y
     *                       {@code limiteSuperior} nulo en las criticas solo
     *                       por abajo, como las plaquetas
     */
    public record LimitesCriticosResponse(
            Double limiteInferior,
            Double limiteSuperior,
            String indicacion
    ) {
    }
}
