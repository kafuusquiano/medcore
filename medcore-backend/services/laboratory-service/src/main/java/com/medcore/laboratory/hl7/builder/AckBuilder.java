package com.medcore.laboratory.hl7.builder;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Construye los acuses de recibo que MedCore devuelve al equipo (regla MC6).
 *
 * <p>Un equipo de laboratorio espera un ACK por cada mensaje que envia. Si no
 * lo recibe, lo reintenta, y segun el modelo acaba bloqueando su cola de
 * salida. Por eso un fallo de parseo NO se responde con un error HTTP a secas:
 * se devuelve un ACK con codigo de error en el segmento MSA, que el equipo
 * entiende y registra, y el fallo queda ademas en el log de MedCore.
 *
 * <p>Codigos del campo MSA-1:
 * <pre>
 *   AA  Application Accept   el mensaje se proceso
 *   AE  Application Error    el mensaje llego pero no se pudo procesar
 *   AR  Application Reject   el mensaje se rechaza; no reintentar igual
 * </pre>
 *
 * <p>El ACK se arma como texto y no con el modelo de HAPI a proposito: es un
 * mensaje de tres segmentos cuya forma no varia, y construirlo a mano evita
 * que un fallo del propio HAPI deje al equipo sin respuesta, que es
 * justamente lo que hay que evitar.
 */
@Component
public class AckBuilder {

    private static final DateTimeFormatter FORMATO_HL7 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String APLICACION = "MEDCORE";
    private static final String INSTALACION = "MEDCORE_LAB";

    /** Separador de segmentos de HL7: retorno de carro, no salto de linea. */
    private static final String FIN_SEGMENTO = "\r";

    /** El mensaje se proceso correctamente. */
    public String aceptado(String mensajeId, String equipo) {
        return construir("AA", mensajeId, equipo, null);
    }

    /**
     * El mensaje llego pero no se pudo procesar. El equipo suele reintentar.
     *
     * @param motivo texto para el campo MSA-3, visible en el equipo
     */
    public String error(String mensajeId, String equipo, String motivo) {
        return construir("AE", mensajeId, equipo, motivo);
    }

    /**
     * El mensaje se rechaza de forma definitiva. Se usa cuando reintentarlo
     * tal cual no va a funcionar nunca, por ejemplo si la muestra no existe.
     */
    public String rechazado(String mensajeId, String equipo, String motivo) {
        return construir("AR", mensajeId, equipo, motivo);
    }

    private String construir(String codigo, String mensajeId, String equipo, String motivo) {
        String ahora = LocalDateTime.now().format(FORMATO_HL7);
        String control = (mensajeId == null || mensajeId.isBlank()) ? "UNKNOWN" : mensajeId;
        String destino = (equipo == null || equipo.isBlank()) ? "EQUIPO" : equipo;

        StringBuilder ack = new StringBuilder();

        // MSH: los campos de emisor y receptor van invertidos respecto al
        // mensaje original, porque ahora MedCore es quien envia.
        ack.append("MSH|^~\\&|")
                .append(APLICACION).append('|')
                .append(INSTALACION).append('|')
                .append(destino).append('|')
                .append(destino).append('|')
                .append(ahora).append("||ACK^R01|")
                .append(control).append("|P|2.5.1")
                .append(FIN_SEGMENTO);

        // MSA: el acuse propiamente dicho.
        ack.append("MSA|").append(codigo).append('|').append(control);
        if (motivo != null && !motivo.isBlank()) {
            ack.append('|').append(sanear(motivo));
        }
        ack.append(FIN_SEGMENTO);

        return ack.toString();
    }

    /**
     * Quita del motivo los caracteres que son separadores en HL7.
     * Sin esto, un mensaje de error con una barra vertical partiria el
     * segmento y el equipo leeria basura.
     */
    private String sanear(String texto) {
        String limpio = texto.replaceAll("[|^~\\\\&\\r\\n]", " ").trim();
        return limpio.length() > 180 ? limpio.substring(0, 180) : limpio;
    }
}
