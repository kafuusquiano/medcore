package com.medcore.laboratory.hl7.adapter;

import com.medcore.laboratory.exception.Hl7ParseException;
import com.medcore.laboratory.hl7.parser.AstmMessageParser;
import com.medcore.laboratory.hl7.parser.Hl7MessageValidator;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import com.medcore.laboratory.hl7.parser.OruR01Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Detecta en que formato llega la trama y delega en el parser que
 * corresponde (regla MC6).
 *
 * <p>Es el unico punto del servicio que sabe que existen dos formatos. Todo
 * lo demas trabaja con {@link Hl7ParsedResult}, de modo que anadir soporte
 * para un tercer formato no obliga a tocar los servicios ni los controllers.
 *
 * <p>La deteccion es por el primer registro de la trama, no por una cabecera
 * HTTP ni por un parametro: los equipos no envian metadatos, solo abren una
 * conexion y escriben.
 */
@Component
public class EquipmentAdapter {

    private static final Logger log = LoggerFactory.getLogger(EquipmentAdapter.class);

    private final OruR01Parser oruParser;
    private final AstmMessageParser astmParser;
    private final Hl7MessageValidator validator;

    public EquipmentAdapter(OruR01Parser oruParser,
                            AstmMessageParser astmParser,
                            Hl7MessageValidator validator) {
        this.oruParser = oruParser;
        this.astmParser = astmParser;
        this.validator = validator;
    }

    /**
     * Interpreta la trama venga en el formato que venga.
     *
     * @throws Hl7ParseException si no es HL7 ni ASTM, o si el contenido no se
     *         puede interpretar
     */
    public Hl7ParsedResult interpretar(String trama) {
        if (trama == null || trama.isBlank()) {
            throw Hl7ParseException.tramaVacia();
        }

        if (validator.pareceHl7(trama)) {
            log.debug("Trama detectada como HL7");
            validator.validar(trama);
            Hl7ParsedResult resultado = oruParser.parsear(trama);
            exigirObservaciones(resultado);
            return resultado;
        }

        if (astmParser.pareceAstm(trama)) {
            log.debug("Trama detectada como ASTM");
            Hl7ParsedResult resultado = astmParser.parsear(trama);
            exigirObservaciones(resultado);
            return resultado;
        }

        throw Hl7ParseException.formatoDesconocido();
    }

    /**
     * Una trama sin ninguna observacion con valor no aporta nada y seria un
     * resultado vacio en el historial del paciente.
     */
    private void exigirObservaciones(Hl7ParsedResult resultado) {
        if (!resultado.tieneObservaciones()) {
            throw Hl7ParseException.sinObservaciones();
        }
    }
}
