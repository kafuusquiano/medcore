package com.medcore.laboratory.hl7.parser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resultado de parsear una trama, ya normalizado.
 *
 * <p>Es el formato comun al que convergen el parser de HL7 y el de ASTM, de
 * modo que el resto del servicio no sepa en que formato llego el mensaje.
 *
 * @param codigoMuestra identificador de la muestra que leyo el equipo
 * @param equipo        identificador del analizador, de MSH-3 en HL7
 * @param mensajeId     control id del mensaje, de MSH-10; se usa en el ACK
 * @param observaciones una entrada por cada prueba medida
 * @param camposCrudos  pares campo/valor sin normalizar, para MongoDB
 */
public record Hl7ParsedResult(
        String codigoMuestra,
        String equipo,
        String mensajeId,
        String formato,
        Instant fechaMensaje,
        List<Observacion> observaciones,
        Map<String, Object> camposCrudos
) {

    /**
     * Una medicion concreta.
     *
     * @param codigoPrueba  codigo que envia el equipo; puede no coincidir con
     *                      el del catalogo de MedCore
     * @param valorNumerico nulo si el equipo envio un valor no numerico
     * @param flagAnormal   campo OBX-8 de HL7: N, L, H, LL, HH
     */
    public record Observacion(
            String codigoPrueba,
            String nombrePrueba,
            Double valorNumerico,
            String valorTexto,
            String unidad,
            String flagAnormal
    ) {
        public boolean esNumerica() {
            return valorNumerico != null;
        }
    }

    public boolean tieneObservaciones() {
        return observaciones != null && !observaciones.isEmpty();
    }
}
