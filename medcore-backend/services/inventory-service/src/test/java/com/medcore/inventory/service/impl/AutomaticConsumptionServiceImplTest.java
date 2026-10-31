package com.medcore.inventory.service.impl;

import com.medcore.commons.event.ResultEvent;
import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.entity.TestReagentUsage;
import com.medcore.inventory.repository.MovementLogRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.repository.TestReagentUsageRepository;
import com.medcore.inventory.service.AutomaticConsumptionService.Descuento;
import com.medcore.inventory.service.MovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomaticConsumptionServiceImplTest {

    @Mock
    private TestReagentUsageRepository usageRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private MovementLogRepository movementRepository;

    @Mock
    private MovementService movementService;

    private AutomaticConsumptionServiceImpl service;

    private Reagent reactivo;

    @BeforeEach
    void setUp() {
        service = new AutomaticConsumptionServiceImpl(
                usageRepository, stockRepository, movementRepository, movementService);

        reactivo = new Reagent("REA-HEM-01", "Reactivo de hemoglobina (Drabkin)",
                "HEMATOLOGIA", "pruebas", "Proveedor", 20, 100, false, null);
        ReflectionTestUtils.setField(reactivo, "id", 1L);

        lenient().when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Descuenta el reactivo de la prueba realizada")
    void descuentaElReactivoDeLaPrueba() {
        Stock lote = lote(50);
        when(movementRepository.existsByIdResultado(500L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-001")).thenReturn(List.of(uso(1)));
        when(stockRepository.lotesConsumibles(1L)).thenReturn(List.of(lote));
        when(stockRepository.buscarParaMover(10L)).thenReturn(Optional.of(lote));

        List<Descuento> descuentos = service.descontarPor(evento(500L, "HEM-001"));

        assertThat(descuentos).hasSize(1);
        assertThat(descuentos.get(0).aplicado()).isEqualTo(1);
        assertThat(descuentos.get(0).completo()).isTrue();
        // Un resultado registrado es una determinacion hecha, y una
        // determinacion hecha es reactivo gastado.
        assertThat(lote.getCantidadActual()).isEqualTo(49);
    }

    @Test
    @DisplayName("Un reenvio del mismo resultado no descuenta dos veces")
    void elReenvioNoDescuentaDosVeces() {
        when(movementRepository.existsByIdResultado(500L)).thenReturn(true);

        List<Descuento> descuentos = service.descontarPor(evento(500L, "HEM-001"));

        // Un descuento doble deja el stock del sistema por debajo del real:
        // el almacen pediria reposicion de algo que tiene.
        assertThat(descuentos).isEmpty();
        verify(stockRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una prueba sin reactivo declarado no es un error")
    void pruebaSinReactivoNoEsError() {
        when(movementRepository.existsByIdResultado(501L)).thenReturn(false);
        when(usageRepository.activosDe("ORI-001")).thenReturn(List.of());

        // El examen de orina se hace con tira reactiva, y la tira no esta en
        // el catalogo de reactivos.
        assertThat(service.descontarPor(evento(501L, "ORI-001"))).isEmpty();
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una prueba con dos reactivos descuenta los dos")
    void pruebaConDosReactivos() {
        Reagent lisante = new Reagent("REA-HEM-03", "Lisante", "HEMATOLOGIA",
                "pruebas", "Proveedor", 10, 50, false, null);
        ReflectionTestUtils.setField(lisante, "id", 2L);

        Stock loteDiluyente = lote(50);
        Stock loteLisante = lote(lisante, 30, 11L);

        when(movementRepository.existsByIdResultado(502L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-003"))
                .thenReturn(List.of(uso(1), uso(lisante, 1)));
        when(stockRepository.lotesConsumibles(1L)).thenReturn(List.of(loteDiluyente));
        when(stockRepository.lotesConsumibles(2L)).thenReturn(List.of(loteLisante));
        when(stockRepository.buscarParaMover(10L)).thenReturn(Optional.of(loteDiluyente));
        when(stockRepository.buscarParaMover(11L)).thenReturn(Optional.of(loteLisante));

        // El recuento leucocitario gasta diluyente y lisante.
        List<Descuento> descuentos = service.descontarPor(evento(502L, "HEM-003"));

        assertThat(descuentos).hasSize(2);
        assertThat(descuentos).allMatch(Descuento::completo);
        assertThat(loteDiluyente.getCantidadActual()).isEqualTo(49);
        assertThat(loteLisante.getCantidadActual()).isEqualTo(29);
    }

    @Test
    @DisplayName("Reparte entre lotes cuando el primero no alcanza")
    void reparteEntreLotes() {
        Stock casiVacio = lote(1);
        Stock siguiente = lote(reactivo, 40, 11L);

        when(movementRepository.existsByIdResultado(503L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-001")).thenReturn(List.of(uso(3)));
        when(stockRepository.lotesConsumibles(1L))
                .thenReturn(List.of(casiVacio, siguiente));
        when(stockRepository.buscarParaMover(10L)).thenReturn(Optional.of(casiVacio));
        when(stockRepository.buscarParaMover(11L)).thenReturn(Optional.of(siguiente));

        List<Descuento> descuentos = service.descontarPor(evento(503L, "HEM-001"));

        // Gasta primero el lote que vence antes, que es el orden que devuelve
        // lotesConsumibles.
        assertThat(descuentos.get(0).aplicado()).isEqualTo(3);
        assertThat(casiVacio.getCantidadActual()).isZero();
        assertThat(siguiente.getCantidadActual()).isEqualTo(38);
    }

    @Test
    @DisplayName("Si no hay stock suficiente, descuenta lo que hay y lo informa")
    void sinStockDescuentaLoQueHay() {
        Stock casiVacio = lote(1);

        when(movementRepository.existsByIdResultado(504L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-001")).thenReturn(List.of(uso(3)));
        when(stockRepository.lotesConsumibles(1L)).thenReturn(List.of(casiVacio));
        when(stockRepository.buscarParaMover(10L)).thenReturn(Optional.of(casiVacio));

        List<Descuento> descuentos = service.descontarPor(evento(504L, "HEM-001"));

        // La prueba ya se hizo y el reactivo ya se gasto. Negarse a apuntarlo
        // porque el sistema cree que no habia no arregla el almacen, solo
        // esconde el descuadre.
        assertThat(descuentos).hasSize(1);
        assertThat(descuentos.get(0).pedido()).isEqualTo(3);
        assertThat(descuentos.get(0).aplicado()).isEqualTo(1);
        assertThat(descuentos.get(0).faltante()).isEqualTo(2);
        assertThat(descuentos.get(0).completo()).isFalse();
        assertThat(casiVacio.getCantidadActual()).isZero();
    }

    @Test
    @DisplayName("Sin stock ninguno no rompe, informa el faltante entero")
    void sinStockNingunoNoRompe() {
        when(movementRepository.existsByIdResultado(505L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-001")).thenReturn(List.of(uso(2)));
        when(stockRepository.lotesConsumibles(1L)).thenReturn(List.of());

        List<Descuento> descuentos = service.descontarPor(evento(505L, "HEM-001"));

        assertThat(descuentos.get(0).aplicado()).isZero();
        assertThat(descuentos.get(0).faltante()).isEqualTo(2);
        // Y no se avisa de minimo, porque no se movio nada.
        verify(movementService, never()).revisarMinimo(anyLong(), anyString());
    }

    @Test
    @DisplayName("Tras descontar, revisa si el reactivo quedo bajo minimo")
    void revisaElMinimoTrasDescontar() {
        Stock lote = lote(21);
        when(movementRepository.existsByIdResultado(506L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-001")).thenReturn(List.of(uso(1)));
        when(stockRepository.lotesConsumibles(1L)).thenReturn(List.of(lote));
        when(stockRepository.buscarParaMover(10L)).thenReturn(Optional.of(lote));

        service.descontarPor(evento(506L, "HEM-001"));

        // Un descuento que no avisa de la reposicion no sirve de nada.
        verify(movementService).revisarMinimo(1L, "req-1");
    }

    @Test
    @DisplayName("El movimiento queda marcado con el resultado que lo provoco")
    void elMovimientoGuardaElResultado() {
        Stock lote = lote(50);
        when(movementRepository.existsByIdResultado(507L)).thenReturn(false);
        when(usageRepository.activosDe("HEM-001")).thenReturn(List.of(uso(1)));
        when(stockRepository.lotesConsumibles(1L)).thenReturn(List.of(lote));
        when(stockRepository.buscarParaMover(10L)).thenReturn(Optional.of(lote));

        service.descontarPor(evento(507L, "HEM-001"));

        var captor = org.mockito.ArgumentCaptor.forClass(MovementLog.class);
        verify(movementRepository).save(captor.capture());

        MovementLog movimiento = captor.getValue();
        // Es lo que hace idempotente el consumo, y lo que permite auditar que
        // se gasto en una prueba concreta.
        assertThat(movimiento.getIdResultado()).isEqualTo(507L);
        assertThat(movimiento.getCodigoMuestra()).isEqualTo("MU-20261031-0042");
        assertThat(movimiento.getMotivo()).contains("HEM-001");
        assertThat(movimiento.getRegistradoPor()).isEqualTo("sistema");
    }

    @Test
    @DisplayName("Un resultado sin identificador o sin prueba se descarta")
    void resultadoIncompletoSeDescarta() {
        assertThat(service.descontarPor(evento(null, "HEM-001"))).isEmpty();
        assertThat(service.descontarPor(evento(508L, null))).isEmpty();
        verify(movementRepository, never()).save(any());
    }

    // ------------------------------------------------------------------

    private TestReagentUsage uso(int cantidad) {
        return uso(reactivo, cantidad);
    }

    private TestReagentUsage uso(Reagent cual, int cantidad) {
        return new TestReagentUsage("HEM-001", cual, cantidad, null);
    }

    private Stock lote(int cantidad) {
        return lote(reactivo, cantidad, 10L);
    }

    private Stock lote(Reagent cual, int cantidad, Long id) {
        Stock stock = new Stock(cual, "L-2026-01", cantidad,
                LocalDate.now().plusMonths(6), LocalDate.now(), "Almacen", null);
        ReflectionTestUtils.setField(stock, "id", id);
        return stock;
    }

    private ResultEvent evento(Long idResultado, String codigoPrueba) {
        return new ResultEvent(
                "evt-1", "result.registered", Instant.now(), "laboratory-service",
                "req-1", idResultado, 70L, "MU-20261031-0042", 10L,
                "ORD-20261031-0007", 3L, "71234567", "Elena Soto", "elena@correo.local",
                "Comas", 5L, codigoPrueba, "Hemoglobina", 13.4, null, "g/dL",
                "NORMAL", false, Instant.now());
    }
}
