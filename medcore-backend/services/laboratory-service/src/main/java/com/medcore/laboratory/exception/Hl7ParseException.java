package com.medcore.laboratory.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Fallo al interpretar una trama de un equipo.
 *
 * <p>Se responde 422 y no 400: la peticion esta bien formada a nivel HTTP,
 * lo que no se puede procesar es su contenido. El equipo recibe ademas un
 * ACK de error en el segmento MSA (regla MC6).
 */
public class Hl7ParseException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public Hl7ParseException(String codigo, String mensaje) {
        super(codigo, HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    public static Hl7ParseException tramaVacia() {
        return new Hl7ParseException("HL7_TRAMA_VACIA",
                "La trama recibida esta vacia.");
    }

    public static Hl7ParseException tramaIlegible(String detalle) {
        return new Hl7ParseException("HL7_TRAMA_ILEGIBLE",
                "No se pudo interpretar la trama recibida: " + detalle);
    }

    public static Hl7ParseException tipoNoSoportado(String tipo) {
        return new Hl7ParseException("HL7_TIPO_NO_SOPORTADO",
                "Solo se aceptan mensajes ORU^R01; se recibio " + tipo + ".");
    }

    public static Hl7ParseException sinSegmentoMsh() {
        return new Hl7ParseException("HL7_SIN_MSH",
                "La trama no tiene segmento MSH y no es un mensaje HL7 valido.");
    }

    public static Hl7ParseException sinIdentificadorDeMuestra() {
        return new Hl7ParseException("HL7_SIN_MUESTRA",
                "La trama no indica a que muestra corresponde el resultado.");
    }

    public static Hl7ParseException sinObservaciones() {
        return new Hl7ParseException("HL7_SIN_OBSERVACIONES",
                "La trama no contiene ninguna observacion con valor.");
    }

    public static Hl7ParseException formatoDesconocido() {
        return new Hl7ParseException("FORMATO_DESCONOCIDO",
                "La trama no corresponde a HL7 ni a ASTM.");
    }
}
