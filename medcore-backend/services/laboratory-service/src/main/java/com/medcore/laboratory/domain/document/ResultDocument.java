package com.medcore.laboratory.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Resultado crudo tal como lo emitio el equipo, en MongoDB.
 *
 * <p>Vive aqui y no en Postgres porque su forma varia segun el fabricante:
 * un analizador de hematologia envia parametros que uno de bioquimica no
 * tiene, y cada uno nombra sus campos a su manera. Guardarlo en columnas
 * obligaria a una migracion por cada equipo nuevo.
 *
 * <p>Conserva la trama original sin tocar. Si manana se descubre que el
 * parser interpretaba mal un campo, se puede reprocesar desde el original en
 * lugar de haber perdido el dato.
 *
 * @param camposCrudos pares campo/valor tal como llegaron, sin normalizar
 */
@Document(collection = "lab_results")
public record ResultDocument(

        @Id String id,

        /**
         * Muestra a la que pertenece. No es unico por si solo: un equipo puede
         * enviar dos tandas de resultados de la misma muestra, por ejemplo un
         * hemograma y luego una formula diferencial.
         */
        String idMuestra,

        /**
         * Identifica el mensaje concreto dentro de la muestra.
         *
         * <p>Junto con {@code idMuestra} forma la clave unica declarada en
         * infra/mongo/init/01-init-collections.js. Distingue el reenvio de una
         * tanda nueva: un analizador que no recibe el ACK a tiempo reenvia el
         * mismo mensaje, con el mismo control id, y eso no debe crear un
         * segundo documento; un envio posterior con otros parametros si.
         *
         * <p>Es el campo MSH-10 de HL7. ASTM no tiene equivalente, asi que
         * para esas tramas se usa la huella de la propia trama, que cumple la
         * misma funcion: identica en un reenvio y distinta en una tanda nueva.
         */
        String idMensaje,

        String codigoMuestra,
        Long idOrden,
        Long idPaciente,

        /** EQUIPO cuando llega por HL7 o ASTM, MANUAL si lo teclea una persona. */
        String origen,

        /** Identificador del analizador que la emitio, del campo MSH-3 de HL7. */
        String equipo,

        /** Trama completa sin modificar, para poder reprocesarla. */
        String tramaOriginal,

        /** Formato de la trama: HL7, ASTM o NINGUNO en la entrada manual. */
        String formato,

        Map<String, Object> camposCrudos,

        Instant fechaRecepcion
) {

    public static ResultDocument deEquipo(Long idMuestra, String codigoMuestra, Long idOrden,
                                          Long idPaciente, String equipo, String mensajeId,
                                          String tramaOriginal, String formato,
                                          Map<String, Object> campos) {
        return new ResultDocument(null, String.valueOf(idMuestra),
                identificadorDeMensaje(mensajeId, tramaOriginal), codigoMuestra, idOrden,
                idPaciente, "EQUIPO", equipo, tramaOriginal, formato,
                campos == null ? Map.of() : Map.copyOf(campos), Instant.now());
    }

    public static ResultDocument manual(Long idMuestra, String codigoMuestra, Long idOrden,
                                        Long idPaciente, Map<String, Object> campos) {
        // En la entrada manual cada registro es un mensaje distinto, de modo
        // que el identificador es la hora: no hay reenvios que detectar.
        return new ResultDocument(null, String.valueOf(idMuestra),
                "MANUAL-" + Instant.now().toEpochMilli(), codigoMuestra, idOrden,
                idPaciente, "MANUAL", null, null, "NINGUNO",
                campos == null ? Map.of() : Map.copyOf(campos), Instant.now());
    }

    /**
     * Identificador del mensaje: el control id si lo trae, o la huella de la
     * trama si no.
     *
     * <p>El hash no busca seguridad, solo distinguir una trama de otra, asi
     * que basta con {@code hashCode}, que es estable dentro de una misma
     * version de Java para una cadena dada. Se formatea en hexadecimal para
     * que se lea igual que un control id.
     */
    private static String identificadorDeMensaje(String mensajeId, String tramaOriginal) {
        if (mensajeId != null && !mensajeId.isBlank()) {
            return mensajeId.trim();
        }
        if (tramaOriginal == null || tramaOriginal.isBlank()) {
            return "SIN-ID-" + Instant.now().toEpochMilli();
        }
        byte[] huella = new byte[]{
                (byte) (tramaOriginal.hashCode() >>> 24),
                (byte) (tramaOriginal.hashCode() >>> 16),
                (byte) (tramaOriginal.hashCode() >>> 8),
                (byte) tramaOriginal.hashCode()};
        return "HUELLA-" + HexFormat.of().formatHex(huella)
                + "-" + tramaOriginal.getBytes(StandardCharsets.UTF_8).length;
    }
}
