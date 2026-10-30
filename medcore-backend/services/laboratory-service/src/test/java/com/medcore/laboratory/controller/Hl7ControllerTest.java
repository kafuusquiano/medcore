package com.medcore.laboratory.controller;

import com.medcore.laboratory.exception.Hl7ParseException;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.hl7.adapter.EquipmentAdapter;
import com.medcore.laboratory.hl7.builder.AckBuilder;
import com.medcore.laboratory.hl7.mapper.Hl7ResultMapper;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import com.medcore.laboratory.service.ResultService;
import com.medcore.laboratory.service.SampleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la respuesta al equipo.
 *
 * <p>Lo que se verifica aqui es la regla que hace distinto a este controller:
 * responde siempre 200 con un ACK, nunca un error HTTP. Un analizador que no
 * recibe ACK reintenta y acaba bloqueando su cola de salida, de modo que
 * devolver un 422 ante una trama mala pararia el laboratorio entero.
 */
@ExtendWith(MockitoExtension.class)
class Hl7ControllerTest {

    @Mock
    private EquipmentAdapter equipmentAdapter;

    @Mock
    private ResultService resultService;

    @Mock
    private SampleService sampleService;

    @Mock
    private Hl7ResultMapper hl7ResultMapper;

    private Hl7Controller controller;

    @BeforeEach
    void prepararControlador() {
        controller = new Hl7Controller(equipmentAdapter, new AckBuilder(),
                resultService, sampleService, hl7ResultMapper);
    }

    @Test
    @DisplayName("Una trama correcta recibe un ACK de aceptacion")
    void unaTramaCorrectaRecibeAckDeAceptacion() {
        when(equipmentAdapter.interpretar(anyString())).thenReturn(tramaParseada());
        when(resultService.registrarDesdeEquipo(any(), anyString(), anyString()))
                .thenReturn(List.of());

        var respuesta = controller.recibirOru("MSH|^~\\&|SYSMEX_XN550|...", "req-1");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).contains("MSA|AA|MSG00042");
        // El ACK va dirigido al equipo que envio la trama.
        assertThat(respuesta.getBody()).contains("SYSMEX_XN550");
    }

    @Test
    @DisplayName("Una trama ilegible recibe 200 con ACK de error, no un 422")
    void unaTramaIlegibleRecibeAckDeError() {
        when(equipmentAdapter.interpretar(anyString()))
                .thenThrow(Hl7ParseException.tramaIlegible("segmento OBX truncado"));

        var respuesta = controller.recibirOru("MSH|roto", "req-2");

        // 200 y no 422: el codigo del fallo va en el MSA, que es donde el
        // equipo lo busca.
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).contains("MSA|AE|");
        assertThat(respuesta.getBody()).contains("segmento OBX truncado");
    }

    @Test
    @DisplayName("Sin control id legible el ACK usa un valor de reserva")
    void sinControlIdLegibleElAckUsaValorDeReserva() {
        // Si la trama no se pudo parsear no hay MSH del que leer el control
        // id, y el equipo necesita el ACK de todas formas.
        when(equipmentAdapter.interpretar(anyString()))
                .thenThrow(Hl7ParseException.sinSegmentoMsh());

        var respuesta = controller.recibirOru("basura", "req-3");

        assertThat(respuesta.getBody()).contains("MSA|AE|UNKNOWN");
        assertThat(respuesta.getBody()).contains("|EQUIPO|EQUIPO|");
    }

    @Test
    @DisplayName("Una muestra inexistente recibe ACK de rechazo definitivo")
    void unaMuestraInexistenteRecibeAckDeRechazo() {
        when(equipmentAdapter.interpretar(anyString())).thenReturn(tramaParseada());
        when(resultService.registrarDesdeEquipo(any(), anyString(), anyString()))
                .thenThrow(LaboratoryException.muestraNoEncontradaPorCodigo("MUE-20261029-0012"));

        var respuesta = controller.recibirOru("MSH|^~\\&|SYSMEX_XN550|...", "req-4");

        // AR y no AE: reintentar la misma trama no va a crear la muestra, de
        // modo que se le dice al equipo que no insista.
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).contains("MSA|AR|MSG00042");
        assertThat(respuesta.getBody()).contains("MUE-20261029-0012");
    }

    @Test
    @DisplayName("Un fallo interno recibe ACK de error para que el equipo reintente")
    void unFalloInternoRecibeAckDeError() {
        when(equipmentAdapter.interpretar(anyString())).thenReturn(tramaParseada());
        when(resultService.registrarDesdeEquipo(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("conexion a la base perdida"));

        var respuesta = controller.recibirOru("MSH|^~\\&|SYSMEX_XN550|...", "req-5");

        // AE: el problema es de MedCore y puede ser transitorio, asi que
        // reintentar si tiene sentido. El detalle interno no se le manda al
        // equipo, solo queda en el log.
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).contains("MSA|AE|MSG00042");
        assertThat(respuesta.getBody()).contains("Error interno de MedCore; reintente");
        assertThat(respuesta.getBody()).doesNotContain("conexion a la base perdida");
    }

    private Hl7ParsedResult tramaParseada() {
        return new Hl7ParsedResult(
                "MUE-20261029-0012",
                "SYSMEX_XN550",
                "MSG00042",
                "HL7",
                Instant.now(),
                List.of(new Hl7ParsedResult.Observacion(
                        "HEM-001", "Hemoglobina", 9.8, "9.8", "g/dL", "L")),
                Map.of("MSH-3", "SYSMEX_XN550"));
    }
}
