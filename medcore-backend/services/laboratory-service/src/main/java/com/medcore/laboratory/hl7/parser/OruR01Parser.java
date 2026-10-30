package com.medcore.laboratory.hl7.parser;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v251.message.ORU_R01;
import ca.uhn.hl7v2.model.v251.segment.MSH;
import ca.uhn.hl7v2.model.v251.segment.OBR;
import ca.uhn.hl7v2.model.v251.segment.OBX;
import ca.uhn.hl7v2.parser.PipeParser;
import com.medcore.laboratory.exception.Hl7ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de mensajes {@code ORU^R01}, que es el que usan los equipos de
 * laboratorio para enviar resultados (regla MC6).
 *
 * <p>Estructura que interesa de un ORU^R01:
 * <pre>
 *   MSH  cabecera: equipo emisor, control id, fecha
 *   PID  paciente
 *   OBR  peticion: identificador de la muestra
 *   OBX  una por cada parametro medido: codigo, valor, unidad, flag
 * </pre>
 *
 * <p>El identificador de la muestra se busca en varios sitios porque cada
 * fabricante lo coloca donde le parece: unos en OBR-3 (Filler Order Number),
 * otros en OBR-2 (Placer Order Number) y otros en OBX-3. Se intenta en ese
 * orden antes de rendirse.
 */
@Component
public class OruR01Parser {

    private static final Logger log = LoggerFactory.getLogger(OruR01Parser.class);

    /** Formato de fecha de HL7: yyyyMMddHHmmss, con la hora opcional. */
    private static final DateTimeFormatter FORMATO_HL7 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PipeParser pipeParser;

    public OruR01Parser(PipeParser pipeParser) {
        this.pipeParser = pipeParser;
    }

    /**
     * Convierte la trama en el formato normalizado del servicio.
     *
     * @throws Hl7ParseException si la trama no es un ORU^R01 legible
     */
    public Hl7ParsedResult parsear(String trama) {
        try {
            Message mensaje = pipeParser.parse(trama);

            if (!(mensaje instanceof ORU_R01 oru)) {
                throw Hl7ParseException.tipoNoSoportado(
                        mensaje.getClass().getSimpleName());
            }

            MSH msh = oru.getMSH();
            String equipo = texto(msh.getSendingApplication().getNamespaceID().getValue());
            String mensajeId = texto(msh.getMessageControlID().getValue());
            Instant fechaMensaje = parsearFecha(msh.getDateTimeOfMessage().getTime().getValue());

            var grupoPaciente = oru.getPATIENT_RESULT();
            var grupoOrden = grupoPaciente.getORDER_OBSERVATION();
            OBR obr = grupoOrden.getOBR();

            String codigoMuestra = extraerCodigoMuestra(obr, grupoOrden.getOBSERVATION(0).getOBX());

            List<Hl7ParsedResult.Observacion> observaciones = new ArrayList<>();
            Map<String, Object> camposCrudos = new LinkedHashMap<>();
            camposCrudos.put("MSH-3", equipo);
            camposCrudos.put("MSH-10", mensajeId);

            int total = grupoOrden.getOBSERVATIONReps();
            for (int i = 0; i < total; i++) {
                OBX obx = grupoOrden.getOBSERVATION(i).getOBX();
                var observacion = leerObx(obx, i, camposCrudos);
                if (observacion != null) {
                    observaciones.add(observacion);
                }
            }

            log.debug("ORU^R01 parseado: muestra {}, equipo {}, {} observaciones",
                    codigoMuestra, equipo, observaciones.size());

            return new Hl7ParsedResult(codigoMuestra, equipo, mensajeId, "HL7",
                    fechaMensaje, observaciones, camposCrudos);

        } catch (Hl7ParseException e) {
            throw e;
        } catch (HL7Exception e) {
            throw Hl7ParseException.tramaIlegible(e.getMessage());
        } catch (RuntimeException e) {
            // Un indice fuera de rango o un segmento ausente llega aqui.
            // Se traduce igualmente a un error de dominio para que el equipo
            // reciba un ACK de error y no una excepcion sin respuesta.
            throw Hl7ParseException.tramaIlegible(e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Lee una OBX.
     *
     * <p>Devuelve null si la observacion no trae valor: los equipos envian
     * OBX vacias para parametros que no midieron, y no son un error.
     */
    private Hl7ParsedResult.Observacion leerObx(OBX obx, int indice,
                                                Map<String, Object> camposCrudos)
            throws HL7Exception {

        String codigo = texto(obx.getObservationIdentifier().getIdentifier().getValue());
        String nombre = texto(obx.getObservationIdentifier().getText().getValue());
        String unidad = texto(obx.getUnits().getIdentifier().getValue());
        String flag = obx.getAbnormalFlags().length > 0
                ? texto(obx.getAbnormalFlags(0).getValue())
                : null;

        String valor = null;
        if (obx.getObservationValueReps() > 0) {
            var dato = obx.getObservationValue(0).getData();
            valor = dato == null ? null : texto(dato.toString());
        }

        if (codigo == null || valor == null) {
            log.debug("OBX {} sin codigo o sin valor; se omite", indice);
            return null;
        }

        camposCrudos.put("OBX-" + indice + "-3", codigo);
        camposCrudos.put("OBX-" + indice + "-5", valor);
        if (unidad != null) {
            camposCrudos.put("OBX-" + indice + "-6", unidad);
        }
        if (flag != null) {
            camposCrudos.put("OBX-" + indice + "-8", flag);
        }

        Double numerico = aNumero(valor);

        return new Hl7ParsedResult.Observacion(
                codigo,
                nombre == null ? codigo : nombre,
                numerico,
                // En una prueba cuantitativa se conserva el texto original
                // igualmente: deja constancia de como lo envio el equipo.
                valor,
                unidad,
                flag);
    }

    /**
     * El identificador de la muestra cambia de sitio segun el fabricante.
     * Se prueban las ubicaciones habituales en orden.
     */
    private String extraerCodigoMuestra(OBR obr, OBX primeraObx) throws HL7Exception {
        String porFiller = texto(obr.getFillerOrderNumber().getEntityIdentifier().getValue());
        if (porFiller != null) {
            return porFiller;
        }
        String porPlacer = texto(obr.getPlacerOrderNumber().getEntityIdentifier().getValue());
        if (porPlacer != null) {
            return porPlacer;
        }
        String porEspecimen = texto(obr.getObr15_SpecimenSource()
                .getSps1_SpecimenSourceNameOrCode().getIdentifier().getValue());
        if (porEspecimen != null) {
            return porEspecimen;
        }
        String porSubId = texto(primeraObx.getObservationSubID().getValue());
        if (porSubId != null) {
            return porSubId;
        }
        throw Hl7ParseException.sinIdentificadorDeMuestra();
    }

    /**
     * Las fechas de HL7 pueden venir con solo la fecha, o con hora parcial.
     * Se completa con ceros lo que falte antes de parsear.
     */
    private Instant parsearFecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return Instant.now();
        }
        try {
            String limpio = valor.trim();
            // Se descarta la fraccion de segundo y el desplazamiento horario.
            int corte = Math.min(limpio.length(), 14);
            String base = limpio.substring(0, corte);
            while (base.length() < 14) {
                base = base + "0";
            }
            return LocalDateTime.parse(base, FORMATO_HL7)
                    .atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException e) {
            log.debug("Fecha HL7 ilegible '{}'; se usa la hora de recepcion", valor);
            return Instant.now();
        }
    }

    private Double aNumero(String valor) {
        try {
            // Algunos equipos envian "<0.5" o ">1000" en los extremos del
            // rango medible. No son numeros, pero si informacion clinica, y
            // se conservan en el texto.
            return Double.valueOf(valor.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String texto(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
