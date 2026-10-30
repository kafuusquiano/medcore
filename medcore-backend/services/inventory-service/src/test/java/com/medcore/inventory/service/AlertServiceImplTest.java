package com.medcore.inventory.service;

import com.medcore.inventory.config.InventoryProperties;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.event.producer.InventoryEventProducer;
import com.medcore.inventory.mapper.ReagentMapper;
import com.medcore.inventory.mapper.StockMapper;
import com.medcore.inventory.repository.ReagentRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    private static final int DIAS_AVISO = 30;

    @Mock
    private ReagentRepository reagentRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private InventoryEventProducer eventProducer;

    private AlertServiceImpl alertService;

    @BeforeEach
    void prepararServicio() {
        StockMapper stockMapper = new StockMapper();
        alertService = new AlertServiceImpl(
                reagentRepository, stockRepository, eventProducer,
                new ReagentMapper(stockMapper), stockMapper,
                new InventoryProperties(DIAS_AVISO, "0 0 6 * * *"));

        lenient().when(stockRepository.totalDisponibleDe(anyLong())).thenReturn(2);
    }

    @Test
    @DisplayName("Una revision sin novedades no publica nada")
    void unaRevisionSinNovedadesNoPublicaNada() {
        when(reagentRepository.reactivosBajoMinimo()).thenReturn(List.of());
        when(stockRepository.lotesVencidos()).thenReturn(List.of());
        when(stockRepository.lotesPorVencer(any())).thenReturn(List.of());

        var resumen = alertService.revisar("req-1");

        assertThat(resumen.total()).isZero();
        verify(eventProducer, times(0)).publicarStockBajo(any(), anyInt(), anyString());
        verify(eventProducer, times(0)).publicarLoteVencido(any(), anyString());
    }

    @Test
    @DisplayName("Publica un aviso por cada reactivo bajo minimo y cada lote con problema")
    void publicaUnAvisoPorCadaCaso() {
        Reagent bajo = reactivo("REA-HEM-01", 10);
        Stock vencido = lote(bajo, "L-VENCIDO", 5, LocalDate.now().minusDays(3));
        Stock porVencer = lote(bajo, "L-PROXIMO", 8, LocalDate.now().plusDays(12));

        when(reagentRepository.reactivosBajoMinimo()).thenReturn(List.of(bajo));
        when(stockRepository.lotesVencidos()).thenReturn(List.of(vencido));
        when(stockRepository.lotesPorVencer(any())).thenReturn(List.of(porVencer));

        var resumen = alertService.revisar("req-2");

        assertThat(resumen.stockBajo()).isEqualTo(1);
        assertThat(resumen.vencidos()).isEqualTo(1);
        assertThat(resumen.porVencer()).isEqualTo(1);
        assertThat(resumen.total()).isEqualTo(3);

        verify(eventProducer).publicarStockBajo(eq(bajo), eq(2), eq("req-2"));
        verify(eventProducer).publicarLoteVencido(eq(vencido), eq("req-2"));
        verify(eventProducer).publicarLoteVencido(eq(porVencer), eq("req-2"));
    }

    @Test
    @DisplayName("Un aviso que falla no corta la revision de los demas")
    void unAvisoQueFallaNoCortaLaRevision() {
        // Una revision que se corta en el tercer reactivo deja sin avisar a
        // los veinte siguientes, y el responsable del almacen no tiene forma
        // de saber que falto.
        Reagent primero = reactivo("REA-HEM-01", 10);
        Reagent segundo = reactivo("REA-BIO-01", 10);
        Reagent tercero = reactivo("REA-MIC-01", 10);

        when(reagentRepository.reactivosBajoMinimo())
                .thenReturn(List.of(primero, segundo, tercero));
        when(stockRepository.lotesVencidos()).thenReturn(List.of());
        when(stockRepository.lotesPorVencer(any())).thenReturn(List.of());

        doThrow(new IllegalStateException("fallo al cargar el proveedor"))
                .when(eventProducer).publicarStockBajo(eq(segundo), anyInt(), anyString());

        var resumen = alertService.revisar("req-3");

        // Los tres cuentan en el resumen; el fallo del segundo queda en el log.
        assertThat(resumen.stockBajo()).isEqualTo(3);
        verify(eventProducer).publicarStockBajo(eq(primero), anyInt(), anyString());
        verify(eventProducer).publicarStockBajo(eq(tercero), anyInt(), anyString());
    }

    @Test
    @DisplayName("La revision no propaga el fallo de un aviso")
    void laRevisionNoPropagaElFallo() {
        Reagent reactivo = reactivo("REA-HEM-01", 10);
        when(reagentRepository.reactivosBajoMinimo()).thenReturn(List.of(reactivo));
        when(stockRepository.lotesVencidos()).thenReturn(List.of());
        when(stockRepository.lotesPorVencer(any())).thenReturn(List.of());

        doThrow(new IllegalStateException("kafka caido"))
                .when(eventProducer).publicarStockBajo(any(), anyInt(), anyString());

        // El scheduler la llama cada madrugada: si propagara, el fallo subiria
        // al planificador en lugar de quedar registrado.
        assertThatCode(() -> alertService.revisar("req-4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Los lotes que requieren atencion llevan los vencidos primero")
    void losVencidosVanPrimero() {
        Reagent reactivo = reactivo("REA-HEM-01", 1);
        Stock vencido = lote(reactivo, "L-VENCIDO", 5, LocalDate.now().minusDays(3));
        Stock porVencer = lote(reactivo, "L-PROXIMO", 8, LocalDate.now().plusDays(12));

        when(stockRepository.lotesVencidos()).thenReturn(List.of(vencido));
        when(stockRepository.lotesPorVencer(any())).thenReturn(List.of(porVencer));

        var atencion = alertService.lotesQueRequierenAtencion();

        // Los vencidos son los que hay que sacar de la estanteria hoy.
        assertThat(atencion).hasSize(2);
        assertThat(atencion.get(0).lote()).isEqualTo("L-VENCIDO");
        assertThat(atencion.get(0).etiquetaSituacion()).isEqualTo("Vencido");
        assertThat(atencion.get(1).lote()).isEqualTo("L-PROXIMO");
        assertThat(atencion.get(1).etiquetaSituacion()).isEqualTo("Por vencer");
    }

    @Test
    @DisplayName("El plazo de aviso por omision es 30 dias si la configuracion no lo trae")
    void elPlazoPorOmisionEs30Dias() {
        // Un arranque con el config-server a medias dejaria el plazo en null y
        // la revision fallaria con NullPointerException en lugar de avisar.
        assertThat(new InventoryProperties(null, null).diasDeAviso()).isEqualTo(30);
        assertThat(new InventoryProperties(-5, null).diasDeAviso()).isEqualTo(30);
        assertThat(new InventoryProperties(15, null).diasDeAviso()).isEqualTo(15);
    }

    // ---- Datos de prueba ---------------------------------------------------

    private Reagent reactivo(String codigo, int stockMinimo) {
        Reagent reactivo = new Reagent(
                codigo, "Reactivo " + codigo, "Hematologia", "frasco",
                "Wiener Lab", stockMinimo, 30, true, null);
        ReflectionTestUtils.setField(reactivo, "id", (long) codigo.hashCode());
        return reactivo;
    }

    private Stock lote(Reagent reactivo, String numeroLote, int cantidad, LocalDate vencimiento) {
        Stock lote = new Stock(reactivo, numeroLote, cantidad, vencimiento,
                LocalDate.now().minusMonths(2), "Estanteria A", null);
        ReflectionTestUtils.setField(lote, "id", (long) numeroLote.hashCode());
        return lote;
    }
}
