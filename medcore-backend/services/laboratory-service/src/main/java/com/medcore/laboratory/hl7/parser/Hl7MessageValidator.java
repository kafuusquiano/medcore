package com.medcore.laboratory.hl7.parser;

import com.medcore.laboratory.exception.Hl7ParseException;
import org.springframework.stereotype.Component;

/**
 * Validacion propia de las tramas, previa al parseo de HAPI.
 *
 * <p>Existe porque la validacion estricta de HAPI esta desactivada: los
 * equipos reales incumplen el estandar en detalles menores y rechazar el
 * mensaje entero por eso perderia un resultado legible. Aqui se comprueba
 * solo lo que el proceso necesita de verdad.
 */
@Component
public class Hl7MessageValidator {

    private static final String SEGMENTO_CABECERA = "MSH";
    private static final String TIPO_ESPERADO = "ORU";

    /** Longitud minima de un MSH util: separadores mas los primeros campos. */
    private static final int LONGITUD_MINIMA = 8;

    /**
     * Comprueba que la trama pueda ser un ORU^R01 antes de entregarla a HAPI.
     *
     * @throws Hl7ParseException si falta el MSH o el tipo no es el esperado
     */
    public void validar(String trama) {
        if (trama == null || trama.isBlank()) {
            throw Hl7ParseException.tramaVacia();
        }

        String limpia = trama.trim();

        if (!limpia.startsWith(SEGMENTO_CABECERA) || limpia.length() < LONGITUD_MINIMA) {
            throw Hl7ParseException.sinSegmentoMsh();
        }

        String tipo = tipoDeMensaje(limpia);
        if (tipo != null && !tipo.startsWith(TIPO_ESPERADO)) {
            throw Hl7ParseException.tipoNoSoportado(tipo);
        }
    }

    /** Indica si la trama parece HL7, para que EquipmentAdapter elija parser. */
    public boolean pareceHl7(String trama) {
        return trama != null && trama.trim().startsWith(SEGMENTO_CABECERA);
    }

    /**
     * Lee MSH-9, el tipo de mensaje.
     *
     * <p>Se hace con un corte de texto y no con HAPI porque esta comprobacion
     * ocurre ANTES de parsear: el objetivo es rechazar pronto lo que no es un
     * ORU, sin pagar el coste de construir el modelo completo.
     */
    private String tipoDeMensaje(String trama) {
        char separador = trama.charAt(3);
        String[] campos = trama.split("\r|\n", 2)[0].split(java.util.regex.Pattern.quote(String.valueOf(separador)));
        // MSH-9 es el noveno campo, pero el separador cuenta como MSH-1, de
        // modo que en el array queda en la posicion 8.
        return campos.length > 8 ? campos[8] : null;
    }
}
