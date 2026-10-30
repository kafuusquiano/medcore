package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;

import java.util.List;
import java.util.Optional;

/**
 * Gestion de los valores criticos.
 *
 * <p>Un valor critico no es solo un resultado anormal: es el que obliga a
 * localizar al medico de inmediato. Se separa del registro del resultado
 * porque lo que sigue es un procedimiento, no un calculo.
 */
public interface CriticalValueService {

    /**
     * Deja constancia del aviso en el log del servicio.
     *
     * <p>El aviso en si lo envia notification-service al consumir
     * {@code result.critical}. Aqui se registra con nivel WARN y el detalle
     * completo para que quede traza local: si el correo o el mensaje fallan,
     * el laboratorio debe poder demostrar que el sistema detecto el valor y
     * cuando.
     */
    void registrarAviso(Result resultado, Sample muestra, String requestId);

    /** Limites criticos configurados para una prueba, si los tiene. */
    Optional<Limites> limitesDe(String codigoPrueba);

    /** Valores criticos del dia, que el laboratorio revisa en cada turno. */
    List<ResultResponse> criticosDeHoy();

    long totalHistorico();

    /**
     * Limites de una prueba.
     *
     * @param limiteInferior puede ser nulo: hay pruebas criticas solo por un
     *                       extremo, como las plaquetas, peligrosas unicamente
     *                       cuando estan muy bajas
     * @param indicacion     que debe hacer el laboratorio al detectarlo
     */
    record Limites(
            String codigoPrueba,
            Double limiteInferior,
            Double limiteSuperior,
            String indicacion
    ) {
    }
}
