package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.dto.request.ResultEntryRequest;
import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;

import java.util.List;

/** Registro e interpretacion de los resultados. */
public interface ResultService {

    /**
     * Entrada manual de un resultado.
     *
     * <p>Se usa cuando la prueba no la emite un equipo conectado: una lectura
     * al microscopio, un cultivo que se informa en texto, o un analizador
     * fuera de linea cuyo valor hay que teclear.
     */
    ResultResponse registrarManual(Long idMuestra, ResultEntryRequest peticion,
                                   String usuario, String requestId);

    /**
     * Resultados que llegaron de un equipo en una trama ya parseada.
     *
     * <p>Una sola trama trae varias observaciones: un hemograma informa
     * hemoglobina, hematocrito, leucocitos y plaquetas de una vez.
     *
     * <p>Las observaciones cuyo codigo no esta en el catalogo se omiten y
     * quedan en el log: un analizador envia parametros internos que el
     * laboratorio no informa, y no son un error.
     *
     * @param tramaOriginal la trama literal como llego, que se guarda en
     *                      MongoDB sin tocar. Permite reprocesar si manana
     *                      se descubre que el parser leia mal un campo, en
     *                      lugar de haber perdido el dato.
     */
    List<ResultResponse> registrarDesdeEquipo(Hl7ParsedResult parseado, String tramaOriginal,
                                              String requestId);

    /**
     * Corrige un resultado ya emitido.
     *
     * <p>No modifica el anterior: anade otro que lo rectifica y lo apunta. Un
     * resultado informado es un registro clinico, y el historial debe
     * conservar lo que se dijo en su momento.
     */
    ResultResponse corregir(Long idResultado, ResultEntryRequest peticion,
                            String usuario, String requestId);

    List<ResultResponse> listarDeMuestra(Long idMuestra);

    ResultResponse obtenerPorId(Long id);

    /** Valores criticos del dia, que el laboratorio revisa en cada turno. */
    List<ResultResponse> criticosDeHoy();

    /** Historial de una prueba en un paciente, para ver su evolucion. */
    List<ResultResponse> historial(Long idPaciente, String codigoPrueba, int limite);
}
