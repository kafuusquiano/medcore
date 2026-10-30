package com.medcore.laboratory.hl7.parser;

import com.medcore.laboratory.exception.Hl7ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de tramas ASTM E1394, el formato de los equipos mas antiguos que
 * no hablan HL7 (regla MC6).
 *
 * <p>ASTM es mas simple que HL7: registros de una letra separados por barras
 * verticales, con los campos separados por el mismo caracter.
 *
 * <pre>
 *   H  cabecera: identifica el equipo
 *   P  paciente
 *   O  peticion: en O-3 va el identificador de la muestra
 *   R  resultado: R-3 codigo, R-4 valor, R-5 unidad, R-7 flag
 *   L  fin de mensaje
 * </pre>
 *
 * <p>Se escribe a mano porque HAPI no cubre ASTM y el formato es lo bastante
 * simple como para no justificar otra dependencia. Devuelve el mismo
 * {@link Hl7ParsedResult} que el parser de HL7, de modo que el resto del
 * servicio no distingue de donde vino el resultado.
 */
@Component
public class AstmMessageParser {

    private static final Logger log = LoggerFactory.getLogger(AstmMessageParser.class);

    private static final String SEPARADOR_CAMPO = "\\|";
    private static final String SEPARADOR_REGISTRO = "\\r\\n|\\r|\\n";

    /** Indica si la trama parece ASTM, para que EquipmentAdapter elija parser. */
    public boolean pareceAstm(String trama) {
        if (trama == null || trama.isBlank()) {
            return false;
        }
        String primera = trama.trim().split(SEPARADOR_REGISTRO, 2)[0];
        // La cabecera ASTM empieza por H seguido del separador de campo.
        return primera.startsWith("H|");
    }

    public Hl7ParsedResult parsear(String trama) {
        if (trama == null || trama.isBlank()) {
            throw Hl7ParseException.tramaVacia();
        }

        String[] registros = trama.trim().split(SEPARADOR_REGISTRO);

        String equipo = null;
        String codigoMuestra = null;
        List<Hl7ParsedResult.Observacion> observaciones = new ArrayList<>();
        Map<String, Object> camposCrudos = new LinkedHashMap<>();

        int indiceResultado = 0;

        for (String registro : registros) {
            if (registro.isBlank()) {
                continue;
            }
            String[] campos = registro.split(SEPARADOR_CAMPO, -1);
            char tipo = registro.charAt(0);

            switch (tipo) {
                case 'H' -> {
                    equipo = campoEn(campos, 4);
                    camposCrudos.put("H-5", equipo);
                }
                case 'O' -> {
                    // O-3 es el identificador de la muestra. Algunos equipos
                    // dejan ese campo vacio y lo escriben en O-4, igual que
                    // en HL7 unos usan OBR-3 y otros OBR-2, asi que se prueban
                    // las dos posiciones antes de rendirse.
                    codigoMuestra = campoEn(campos, 2);
                    if (codigoMuestra == null) {
                        codigoMuestra = campoEn(campos, 3);
                    }
                    camposCrudos.put("O-3", codigoMuestra);
                }
                case 'R' -> {
                    var observacion = leerResultado(campos, indiceResultado, camposCrudos);
                    if (observacion != null) {
                        observaciones.add(observacion);
                        indiceResultado++;
                    }
                }
                default -> {
                    // P y L no aportan datos que el servicio necesite.
                }
            }
        }

        if (codigoMuestra == null) {
            throw Hl7ParseException.sinIdentificadorDeMuestra();
        }

        log.debug("ASTM parseado: muestra {}, equipo {}, {} observaciones",
                codigoMuestra, equipo, observaciones.size());

        return new Hl7ParsedResult(codigoMuestra, equipo, null, "ASTM",
                Instant.now(), observaciones, camposCrudos);
    }

    private Hl7ParsedResult.Observacion leerResultado(String[] campos, int indice,
                                                      Map<String, Object> camposCrudos) {
        // R-3 lleva el codigo, a veces con sub-componentes separados por ^.
        String codigoCompleto = campoEn(campos, 2);
        String valor = campoEn(campos, 3);

        if (codigoCompleto == null || valor == null) {
            return null;
        }

        String codigo = codigoCompleto.split("\\^")[0];
        String unidad = campoEn(campos, 4);
        String flag = campoEn(campos, 6);

        camposCrudos.put("R-" + indice + "-3", codigoCompleto);
        camposCrudos.put("R-" + indice + "-4", valor);

        return new Hl7ParsedResult.Observacion(
                codigo, codigo, aNumero(valor), valor, unidad, flag);
    }

    private String campoEn(String[] campos, int indice) {
        if (indice >= campos.length) {
            return null;
        }
        String valor = campos[indice];
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private Double aNumero(String valor) {
        try {
            return Double.valueOf(valor.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
