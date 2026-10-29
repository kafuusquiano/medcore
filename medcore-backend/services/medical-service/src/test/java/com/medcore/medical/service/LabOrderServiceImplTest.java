package com.medcore.medical.service;

import com.medcore.medical.domain.dto.request.LabOrderRequest;
import com.medcore.medical.domain.entity.LabOrder;
import com.medcore.medical.domain.entity.MedicalAttention;
import com.medcore.medical.domain.enums.AttentionStatus;
import com.medcore.medical.domain.enums.LabOrderPriority;
import com.medcore.medical.domain.enums.LabOrderStatus;
import com.medcore.medical.event.producer.LabOrderEventProducer;
import com.medcore.medical.exception.MedicalException;
import com.medcore.medical.mapper.LabOrderMapper;
import com.medcore.medical.repository.LabOrderRepository;
import com.medcore.medical.repository.MedicalAttentionRepository;
import com.medcore.medical.service.impl.LabOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabOrderServiceImplTest {

    private static final String REQUEST_ID = "req-orden-1";

    @Mock
    private LabOrderRepository orderRepository;
    @Mock
    private MedicalAttentionRepository attentionRepository;
    @Mock
    private LabOrderEventProducer eventProducer;

    private LabOrderServiceImpl service;

    private MedicalAttention atencion;

    @BeforeEach
    void preparar() {
        service = new LabOrderServiceImpl(orderRepository, attentionRepository,
                eventProducer, new LabOrderMapper());

        atencion = new MedicalAttention(10L, 2L, "12345678", "Ana Torres",
                "ana@correo.com", 77L, "Dr. Vega", "Dolor de garganta");
        ReflectionTestUtils.setField(atencion, "id", 1L);

        when(attentionRepository.findById(1L)).thenReturn(Optional.of(atencion));
        when(orderRepository.contarEmitidasHoy()).thenReturn(6L);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            LabOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", 50L);
            }
            return o;
        });
    }

    private LabOrderRequest request(LabOrderPriority prioridad,
                                    List<LabOrderRequest.PruebaSolicitada> pruebas) {
        return new LabOrderRequest(prioridad, "Descartar infeccion", pruebas);
    }

    @Test
    @DisplayName("emitir_atencionAbierta_creaOrdenYPublicaEvento")
    void emitir_atencionAbierta_creaOrdenYPublicaEvento() {
        var respuesta = service.emitir(1L, request(LabOrderPriority.URGENTE, List.of(
                new LabOrderRequest.PruebaSolicitada("HEM-001", "Hemograma", "Ayuno"))), REQUEST_ID);

        assertThat(respuesta.estado()).isEqualTo(LabOrderStatus.EMITIDA);
        assertThat(respuesta.prioridad()).isEqualTo(LabOrderPriority.URGENTE);
        assertThat(respuesta.pruebas()).hasSize(1);
        verify(eventProducer).publicarCreada(any(LabOrder.class), anyString());
    }

    @Test
    @DisplayName("emitir_codigoCorrelativoDelDia")
    void emitir_codigoCorrelativoDelDia() {
        var respuesta = service.emitir(1L, request(null, List.of(
                new LabOrderRequest.PruebaSolicitada("HEM-001", "Hemograma", null))), REQUEST_ID);

        // Hay 6 emitidas hoy, asi que esta es la septima.
        assertThat(respuesta.codigo()).startsWith("ORD-").endsWith("-0007");
    }

    @Test
    @DisplayName("emitir_pruebaRepetidaEnLaPeticion_seDescarta")
    void emitir_pruebaRepetidaEnLaPeticion_seDescarta() {
        // El indice ux_order_test la rechazaria con un error tecnico; se
        // prefiere ignorarla en silencio.
        var respuesta = service.emitir(1L, request(null, List.of(
                new LabOrderRequest.PruebaSolicitada("HEM-001", "Hemograma", null),
                new LabOrderRequest.PruebaSolicitada("PCR-001", "Proteina C", null),
                new LabOrderRequest.PruebaSolicitada("HEM-001", "Repetida", null))), REQUEST_ID);

        assertThat(respuesta.pruebas()).hasSize(2);
    }

    @Test
    @DisplayName("emitir_sinPruebas_lanzaOrdenSinPruebas")
    void emitir_sinPruebas_lanzaOrdenSinPruebas() {
        assertThatThrownBy(() -> service.emitir(1L, request(null, List.of()), REQUEST_ID))
                .isInstanceOf(MedicalException.class)
                .hasFieldOrPropertyWithValue("codigo", "ORDEN_SIN_PRUEBAS");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("emitir_atencionFinalizada_lanzaAtencionCerrada")
    void emitir_atencionFinalizada_lanzaAtencionCerrada() {
        ReflectionTestUtils.setField(atencion, "estado", AttentionStatus.FINALIZADA);

        assertThatThrownBy(() -> service.emitir(1L, request(null, List.of(
                new LabOrderRequest.PruebaSolicitada("HEM-001", "Hemograma", null))), REQUEST_ID))
                .isInstanceOf(MedicalException.class)
                .hasFieldOrPropertyWithValue("codigo", "ATENCION_CERRADA");

        verify(eventProducer, never()).publicarCreada(any(), anyString());
    }

    @Test
    @DisplayName("emitir_sinPrioridad_usaRutinaPorDefecto")
    void emitir_sinPrioridad_usaRutinaPorDefecto() {
        var respuesta = service.emitir(1L, request(null, List.of(
                new LabOrderRequest.PruebaSolicitada("HEM-001", "Hemograma", null))), REQUEST_ID);

        assertThat(respuesta.prioridad()).isEqualTo(LabOrderPriority.RUTINA);
    }

    @Test
    @DisplayName("anular_ordenEmitida_laAnula")
    void anular_ordenEmitida_laAnula() {
        LabOrder orden = new LabOrder("ORD-20261029-0001", atencion, LabOrderPriority.RUTINA, null);
        ReflectionTestUtils.setField(orden, "id", 50L);
        when(orderRepository.findWithPruebasById(50L)).thenReturn(Optional.of(orden));

        var respuesta = service.anular(50L, "Error de registro");

        assertThat(respuesta.estado()).isEqualTo(LabOrderStatus.ANULADA);
        assertThat(respuesta.motivoAnulacion()).isEqualTo("Error de registro");
        assertThat(respuesta.puedeAnularse()).isFalse();
    }

    @Test
    @DisplayName("anular_ordenEnProceso_lanzaOrdenNoAnulable")
    void anular_ordenEnProceso_lanzaOrdenNoAnulable() {
        LabOrder orden = new LabOrder("ORD-20261029-0002", atencion, LabOrderPriority.RUTINA, null);
        ReflectionTestUtils.setField(orden, "id", 51L);
        orden.marcarEnProceso();
        when(orderRepository.findWithPruebasById(51L)).thenReturn(Optional.of(orden));

        // Ya se tomo la muestra al paciente: anularla dejaria al laboratorio
        // con material sin orden que lo justifique.
        assertThatThrownBy(() -> service.anular(51L, "Tarde"))
                .isInstanceOf(MedicalException.class)
                .hasFieldOrPropertyWithValue("codigo", "ORDEN_NO_ANULABLE");

        assertThat(orden.getEstado()).isEqualTo(LabOrderStatus.EN_PROCESO);
    }

    @Test
    @DisplayName("transicionesDeEstado_sonLasDeclaradasEnElEnum")
    void transicionesDeEstado_sonLasDeclaradasEnElEnum() {
        assertThat(LabOrderStatus.EMITIDA.puedePasarA(LabOrderStatus.EN_PROCESO)).isTrue();
        assertThat(LabOrderStatus.EMITIDA.puedePasarA(LabOrderStatus.COMPLETADA)).isFalse();
        assertThat(LabOrderStatus.EN_PROCESO.admiteAnulacion()).isFalse();
        assertThat(LabOrderStatus.COMPLETADA.siguientesValidos()).isEmpty();
    }

    @Test
    @DisplayName("atencion_finalizarSinDiagnostico_lanzaIllegalStateYNoCambiaElEstado")
    void atencion_finalizarSinDiagnostico_lanzaIllegalStateYNoCambiaElEstado() {
        assertThatThrownBy(() -> atencion.finalizar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diagnostico");

        // Se valida antes de cambiar el estado: si fuera al reves, el objeto
        // quedaria marcado como finalizado en memoria sin haberlo sido.
        assertThat(atencion.getEstado()).isEqualTo(AttentionStatus.EN_CURSO);
    }

    @Test
    @DisplayName("atencion_imc_seCalculaDesdePesoYTalla")
    void atencion_imc_seCalculaDesdePesoYTalla() {
        atencion.registrarSignosVitales(37.0, 120, 80, 72, 70.0, 170.0);

        assertThat(atencion.imc()).isEqualTo(24.22);
    }

    @Test
    @DisplayName("atencion_imc_sinTalla_devuelveNulo")
    void atencion_imc_sinTalla_devuelveNulo() {
        atencion.registrarSignosVitales(37.0, 120, 80, 72, 70.0, null);

        assertThat(atencion.imc()).isNull();
    }
}
