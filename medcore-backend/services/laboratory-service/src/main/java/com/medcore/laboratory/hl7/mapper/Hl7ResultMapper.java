package com.medcore.laboratory.hl7.mapper;

import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Construye una trama {@code ORU^R01} con los resultados de MedCore.
 *
 * <p>Es el camino inverso al del parser: sirve para entregar resultados a
 * otro sistema que hable HL7, como el historico del hospital o una
 * aseguradora. El flujo entrante no la usa.
 *
 * <p>Se arma como texto y no con el modelo de HAPI porque la estructura es
 * fija y conocida, y escribirla directamente deja ver en el propio codigo que
 * campo ocupa cada posicion, que es justo lo que hay que revisar cuando el
 * sistema receptor se queja.
 */
@Component
public class Hl7ResultMapper {

    private static final DateTimeFormatter FORMATO_HL7 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Separador de segmentos de HL7: retorno de carro, no salto de linea. */
    private static final String FIN_SEGMENTO = "\r";

    private static final String APLICACION = "MEDCORE";
    private static final String INSTALACION = "MEDCORE_LAB";

    /**
     * Arma la trama de una muestra con sus resultados.
     *
     * @param destino identificador del sistema receptor, para MSH-5
     */
    public String aOruR01(Sample muestra, List<Result> resultados, String destino) {
        String ahora = LocalDateTime.now().format(FORMATO_HL7);
        String receptor = (destino == null || destino.isBlank()) ? "EXTERNO" : destino;

        StringBuilder trama = new StringBuilder();

        trama.append("MSH|^~\\&|")
                .append(APLICACION).append('|')
                .append(INSTALACION).append('|')
                .append(receptor).append('|')
                .append(receptor).append('|')
                .append(ahora).append("||ORU^R01|")
                .append(muestra.getCodigo()).append("|P|2.5.1")
                .append(FIN_SEGMENTO);

        // PID-3 lleva el documento del paciente, que es el identificador con
        // el que lo conocen los sistemas externos; PID-5 su nombre, con el
        // apellido antes del nombre separado por circunflejo, como exige HL7.
        trama.append("PID|1||")
                .append(sanear(muestra.getDniPaciente())).append("||")
                .append(sanear(muestra.getNombrePaciente())).append("|||")
                .append(sexoHl7(muestra.getSexoPaciente()))
                .append(FIN_SEGMENTO);

        // OBR-3 es el identificador de la muestra, el mismo campo del que el
        // parser lo lee al recibir.
        trama.append("OBR|1||")
                .append(sanear(muestra.getCodigo())).append('|')
                .append("||").append(fechaHl7(muestra))
                .append(FIN_SEGMENTO);

        int indice = 1;
        for (Result resultado : resultados) {
            trama.append(obx(resultado, indice++)).append(FIN_SEGMENTO);
        }

        return trama.toString();
    }

    /**
     * Un segmento OBX por cada resultado.
     *
     * <p>OBX-2 declara el tipo del valor: NM para numerico y ST para texto.
     * El receptor lo usa para saber si puede hacer aritmetica con el campo
     * siguiente, asi que informarlo mal es peor que no informarlo.
     */
    private String obx(Result resultado, int indice) {
        boolean numerico = resultado.getValorNumerico() != null;

        StringBuilder obx = new StringBuilder();
        obx.append("OBX|").append(indice).append('|')
                .append(numerico ? "NM" : "ST").append('|')
                .append(sanear(resultado.getCodigoPrueba())).append('^')
                .append(sanear(resultado.getNombrePrueba())).append("||")
                .append(numerico
                        ? String.valueOf(resultado.getValorNumerico())
                        : sanear(resultado.getValorTexto()))
                .append('|')
                .append(sanear(resultado.getUnidad())).append('|')
                .append(sanear(resultado.getRangoReferencia())).append('|')
                // OBX-8: el flag de anormalidad, en la codificacion del
                // estandar, que es la que guarda el enum.
                .append(resultado.getInterpretacion().codigoHl7()).append("|||")
                // OBX-11: F de Final. Una correccion se declara C, para que el
                // receptor sepa que rectifica un valor que ya recibio.
                .append(resultado.getIdResultadoCorregido() == null ? "F" : "C");

        return obx.toString();
    }

    /** HL7 codifica el sexo con una letra: M, F o U de desconocido. */
    private String sexoHl7(String sexo) {
        if (sexo == null) {
            return "U";
        }
        return switch (sexo.toUpperCase()) {
            case "MASCULINO" -> "M";
            case "FEMENINO" -> "F";
            default -> "U";
        };
    }

    private String fechaHl7(Sample muestra) {
        return muestra.getFechaToma() == null
                ? ""
                : LocalDateTime.ofInstant(muestra.getFechaToma(), ZoneId.systemDefault())
                        .format(FORMATO_HL7);
    }

    /**
     * Quita del texto los caracteres que HL7 usa como separadores.
     *
     * <p>Sin esto, el nombre de una prueba con una barra vertical partiria el
     * segmento y el receptor leeria los campos corridos de posicion.
     */
    private String sanear(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("[|^~\\\\&\\r\\n]", " ").trim();
    }
}
