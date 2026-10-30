package com.medcore.laboratory.hl7;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import com.medcore.laboratory.exception.Hl7ParseException;
import com.medcore.laboratory.hl7.adapter.EquipmentAdapter;
import com.medcore.laboratory.hl7.builder.AckBuilder;
import com.medcore.laboratory.hl7.parser.AstmMessageParser;
import com.medcore.laboratory.hl7.parser.Hl7MessageValidator;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import com.medcore.laboratory.hl7.parser.OruR01Parser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del parseo de tramas de equipos.
 *
 * <p>No usan mocks: el parser trabaja contra HAPI, y simularlo probaria la
 * simulacion y no la integracion, que es justo lo que puede fallar. Se parsean
 * tramas reales, incluida la del recurso que acompana al servicio.
 */
class OruR01ParserTest {

    /** Separador de segmentos de HL7: retorno de carro, no salto de linea. */
    private static final String CR = "\r";

    private static HapiContext context;
    private static OruR01Parser parser;
    private static AstmMessageParser astmParser;
    private static Hl7MessageValidator validator;
    private static EquipmentAdapter adapter;
    private static AckBuilder ackBuilder;

    @BeforeAll
    static void prepararContexto() {
        context = new DefaultHapiContext();
        context.setValidationContext(new NoValidation());
        parser = new OruR01Parser(context.getPipeParser());
        astmParser = new AstmMessageParser();
        validator = new Hl7MessageValidator();
        adapter = new EquipmentAdapter(parser, astmParser, validator);
        ackBuilder = new AckBuilder();
    }

    @AfterAll
    static void cerrarContexto() throws IOException {
        context.close();
    }

    @Test
    @DisplayName("Parsea un ORU^R01 con varias observaciones")
    void parseaUnOruConVariasObservaciones() {
        Hl7ParsedResult resultado = parser.parsear(hemogramaMinimo());

        assertThat(resultado.codigoMuestra()).isEqualTo("MUE-20261029-0012");
        assertThat(resultado.equipo()).isEqualTo("SYSMEX_XN550");
        assertThat(resultado.mensajeId()).isEqualTo("MSG00042");
        assertThat(resultado.formato()).isEqualTo("HL7");
        assertThat(resultado.observaciones()).hasSize(2);

        var hemoglobina = resultado.observaciones().get(0);
        assertThat(hemoglobina.codigoPrueba()).isEqualTo("HEM-001");
        assertThat(hemoglobina.nombrePrueba()).isEqualTo("Hemoglobina");
        assertThat(hemoglobina.valorNumerico()).isEqualTo(9.8);
        assertThat(hemoglobina.unidad()).isEqualTo("g/dL");
        assertThat(hemoglobina.flagAnormal()).isEqualTo("L");
    }

    @Test
    @DisplayName("Parsea la trama de ejemplo que acompana al servicio")
    void parseaLaTramaDeEjemplo() throws IOException {
        String trama = leerRecurso("/hl7/samples/oru_r01_hemograma.hl7");

        Hl7ParsedResult resultado = adapter.interpretar(trama);

        assertThat(resultado.codigoMuestra()).isEqualTo("MUE-20261029-0012");
        // Cinco OBX en el archivo, incluida una de un parametro interno del
        // analizador. El parser las entrega todas; descartar las que no estan
        // en el catalogo es trabajo de ResultService.
        assertThat(resultado.observaciones()).hasSize(5);
        assertThat(resultado.observaciones())
                .extracting(Hl7ParsedResult.Observacion::codigoPrueba)
                .containsExactly("HEM-001", "HEM-002", "HEM-003", "HEM-004", "XN-RBC");
    }

    @Test
    @DisplayName("Omite las OBX que no traen valor")
    void omiteLasObxSinValor() {
        // Los equipos envian OBX vacias para los parametros que no midieron.
        String trama = String.join(CR,
                cabecera(),
                "PID|1||44556677||PEREZ^MARIA|||F",
                "OBR|1||MUE-20261029-0012",
                "OBX|1|NM|HEM-001^Hemoglobina||9.8|g/dL||L|||F",
                "OBX|2|NM|HEM-002^Hematocrito|||%|||||F") + CR;

        Hl7ParsedResult resultado = parser.parsear(trama);

        assertThat(resultado.observaciones()).hasSize(1);
        assertThat(resultado.observaciones().get(0).codigoPrueba()).isEqualTo("HEM-001");
    }

    @Test
    @DisplayName("Conserva como texto un valor no numerico")
    void conservaComoTextoUnValorNoNumerico() {
        // Los analizadores informan ">1000" o "<0.5" en los extremos del
        // rango medible. No son numeros, pero si informacion clinica.
        String trama = String.join(CR,
                cabecera(),
                "PID|1||44556677||PEREZ^MARIA|||F",
                "OBR|1||MUE-20261029-0012",
                "OBX|1|ST|BIO-001^Glucosa||>600|mg/dL||HH|||F") + CR;

        var observacion = parser.parsear(trama).observaciones().get(0);

        assertThat(observacion.valorNumerico()).isNull();
        assertThat(observacion.valorTexto()).isEqualTo(">600");
        assertThat(observacion.esNumerica()).isFalse();
    }

    @Test
    @DisplayName("Busca el codigo de muestra en OBR-2 cuando OBR-3 viene vacio")
    void buscaElCodigoEnObr2CuandoObr3EstaVacio() {
        // Cada fabricante coloca el identificador donde le parece.
        String trama = String.join(CR,
                cabecera(),
                "PID|1||44556677||PEREZ^MARIA|||F",
                "OBR|1|MUE-20261029-0099||HEMOGRAMA",
                "OBX|1|NM|HEM-001^Hemoglobina||13.1|g/dL||N|||F") + CR;

        assertThat(parser.parsear(trama).codigoMuestra()).isEqualTo("MUE-20261029-0099");
    }

    @Test
    @DisplayName("Rechaza una trama sin identificador de muestra")
    void rechazaUnaTramaSinIdentificadorDeMuestra() {
        String trama = String.join(CR,
                cabecera(),
                "PID|1||44556677||PEREZ^MARIA|||F",
                "OBR|1",
                "OBX|1|NM|HEM-001^Hemoglobina||13.1|g/dL||N|||F") + CR;

        assertThatThrownBy(() -> parser.parsear(trama))
                .isInstanceOf(Hl7ParseException.class)
                .hasMessageContaining("no indica a que muestra");
    }

    @Test
    @DisplayName("El validador rechaza lo que no es un ORU")
    void elValidadorRechazaLoQueNoEsUnOru() {
        String adt = "MSH|^~\\&|HIS|HOSPITAL|MEDCORE|MEDCORE_LAB|20261029143012||ADT^A01|1|P|2.5.1"
                + CR;

        assertThatThrownBy(() -> validator.validar(adt))
                .isInstanceOf(Hl7ParseException.class)
                .hasMessageContaining("Solo se aceptan mensajes ORU^R01");
    }

    @Test
    @DisplayName("El validador rechaza una trama sin MSH")
    void elValidadorRechazaUnaTramaSinMsh() {
        assertThatThrownBy(() -> validator.validar("OBX|1|NM|HEM-001||9.8"))
                .isInstanceOf(Hl7ParseException.class)
                .hasMessageContaining("no tiene segmento MSH");

        assertThatThrownBy(() -> validator.validar("  "))
                .isInstanceOf(Hl7ParseException.class)
                .hasMessageContaining("esta vacia");
    }

    @Test
    @DisplayName("El adaptador detecta ASTM y lo parsea")
    void elAdaptadorDetectaAstm() throws IOException {
        String trama = leerRecurso("/hl7/samples/astm_bioquimica.astm");

        Hl7ParsedResult resultado = adapter.interpretar(trama);

        assertThat(resultado.formato()).isEqualTo("ASTM");
        assertThat(resultado.codigoMuestra()).isEqualTo("MUE-20261029-0013");
        assertThat(resultado.equipo()).isEqualTo("ROCHE_COBAS_C311^1");
        assertThat(resultado.observaciones()).hasSize(2);

        var glucosa = resultado.observaciones().get(0);
        // El codigo llega con sub-componentes; el parser se queda con el primero.
        assertThat(glucosa.codigoPrueba()).isEqualTo("BIO-001");
        assertThat(glucosa.valorNumerico()).isEqualTo(412.0);
        assertThat(glucosa.flagAnormal()).isEqualTo("HH");
    }

    @Test
    @DisplayName("El adaptador rechaza un formato que no reconoce")
    void elAdaptadorRechazaUnFormatoDesconocido() {
        assertThatThrownBy(() -> adapter.interpretar("{\"resultado\": 9.8}"))
                .isInstanceOf(Hl7ParseException.class)
                .hasMessageContaining("no corresponde a HL7 ni a ASTM");
    }

    @Test
    @DisplayName("El adaptador rechaza una trama sin ninguna observacion con valor")
    void elAdaptadorRechazaUnaTramaSinObservaciones() {
        String trama = String.join(CR,
                cabecera(),
                "PID|1||44556677||PEREZ^MARIA|||F",
                "OBR|1||MUE-20261029-0012",
                "OBX|1|NM|HEM-001^Hemoglobina|||g/dL|||||F") + CR;

        assertThatThrownBy(() -> adapter.interpretar(trama))
                .isInstanceOf(Hl7ParseException.class)
                .hasMessageContaining("ninguna observacion con valor");
    }

    @Test
    @DisplayName("El ACK lleva el control id del mensaje original")
    void elAckLlevaElControlIdOriginal() {
        String ack = ackBuilder.aceptado("MSG00042", "SYSMEX_XN550");

        assertThat(ack).startsWith("MSH|^~\\&|MEDCORE|MEDCORE_LAB|SYSMEX_XN550");
        assertThat(ack).contains("MSA|AA|MSG00042");
        // Separador de segmentos: retorno de carro, no salto de linea.
        assertThat(ack).contains(CR).doesNotContain("\n");
    }

    @Test
    @DisplayName("El ACK de error sanea los separadores del motivo")
    void elAckDeErrorSaneaElMotivo() {
        // Una barra vertical en el motivo partiria el segmento y el equipo
        // leeria los campos corridos de posicion.
        String ack = ackBuilder.error("MSG00042", "SYSMEX_XN550",
                "Campo OBX|5 ilegible^revisar");

        assertThat(ack).contains("MSA|AE|MSG00042|Campo OBX 5 ilegible revisar");
    }

    @Test
    @DisplayName("El ACK usa valores de reserva cuando no se pudo parsear la trama")
    void elAckUsaValoresDeReserva() {
        // Es el caso real de una trama ilegible: no hay control id porque no
        // se llego a leer el MSH, y el equipo necesita un ACK de todas formas.
        String ack = ackBuilder.error(null, null, "Trama truncada");

        assertThat(ack).contains("MSA|AE|UNKNOWN|Trama truncada");
        assertThat(ack).contains("|EQUIPO|EQUIPO|");
    }

    // ---- Apoyo -------------------------------------------------------------

    private static String cabecera() {
        return "MSH|^~\\&|SYSMEX_XN550|LAB_CENTRAL|MEDCORE|MEDCORE_LAB|"
                + "20261029143012||ORU^R01|MSG00042|P|2.5.1";
    }

    private static String hemogramaMinimo() {
        return String.join(CR,
                cabecera(),
                "PID|1||44556677||PEREZ QUISPE^MARIA ELENA|||F",
                "OBR|1||MUE-20261029-0012|HEMOGRAMA^Hemograma completo||20261029140000",
                "OBX|1|NM|HEM-001^Hemoglobina||9.8|g/dL|12.0 - 15.5|L|||F",
                "OBX|2|NM|HEM-002^Hematocrito||29.4|%|36.0 - 46.0|L|||F") + CR;
    }

    private static String leerRecurso(String ruta) throws IOException {
        try (var entrada = Objects.requireNonNull(
                OruR01ParserTest.class.getResourceAsStream(ruta),
                "No se encontro el recurso " + ruta)) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
