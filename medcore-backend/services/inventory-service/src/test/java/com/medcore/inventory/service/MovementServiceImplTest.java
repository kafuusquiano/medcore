package com.medcore.inventory.service;

import com.medcore.inventory.domain.dto.request.MovementRequest;
import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.enums.MovementType;
import com.medcore.inventory.event.producer.InventoryEventProducer;
import com.medcore.inventory.exception.InventoryException;
import com.medcore.inventory.mapper.StockMapper;
import com.medcore.inventory.repository.MovementLogRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.service.impl.MovementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del registro de movimientos.
 *
 * <p>Fuera de una transaccion real, {@code publicarTrasCommit} publica de
 * inmediato porque no hay sincronizacion activa, lo que permite verificar
 * aqui que se avisa de lo correcto.
 */
@ExtendWith(MockitoExtension.class)
class MovementServiceImplTest {

    private static final Long ID_REACTIVO = 3L;
    private static final Long ID_LOTE = 7L;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private MovementLogRepository movementRepository;

    @Mock
    private InventoryEventProducer eventProducer;

    private MovementServiceImpl movementService;

    @BeforeEach
    void prepararServicio() {
        movementService = new MovementServiceImpl(
                stockRepository, movementRepository, eventProducer, new StockMapper());

        lenient().when(movementRepository.save(any(MovementLog.class)))
                .thenAnswer(invocacion -> {
                    MovementLog guardado = invocacion.getArgument(0);
                    if (guardado.getId() == null) {
                        ReflectionTestUtils.setField(guardado, "id", 100L);
                    }
                    if (guardado.getFechaMovimiento() == null) {
                        ReflectionTestUtils.setField(guardado, "fechaMovimiento", Instant.now());
                    }
                    return guardado;
                });
    }

    @Test
    @DisplayName("Una salida descuenta del lote y registra el antes y el despues")
    void unaSalidaDescuentaYRegistraElAntesYDespues() {
        Stock lote = lote(20, LocalDate.now().plusMonths(6), 5);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(17);

        var respuesta = movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, 3, null, "MUE-20261029-0001"),
                "tecnico1", "req-1");

        assertThat(lote.getCantidadActual()).isEqualTo(17);
        // La cantidad se guarda con signo, y el antes y el despues permiten
        // rastrear un recuento que no cuadra sin sumar toda la cadena.
        assertThat(respuesta.cantidad()).isEqualTo(-3);
        assertThat(respuesta.cantidadAnterior()).isEqualTo(20);
        assertThat(respuesta.cantidadResultante()).isEqualTo(17);
        assertThat(respuesta.codigoMuestra()).isEqualTo("MUE-20261029-0001");
        // 17 sigue por encima del minimo de 5: no hay aviso.
        verify(eventProducer, never()).publicarStockBajo(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Una salida que deja el reactivo bajo minimo publica stock.low")
    void unaSalidaQueDejaBajoMinimoAvisa() {
        Stock lote = lote(8, LocalDate.now().plusMonths(6), 5);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));
        // Tras descontar 5 quedan 3, por debajo del minimo.
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(3);

        movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, 5, null, null),
                "tecnico1", "req-2");

        // Se avisa en el momento y no en la revision nocturna: si un consumo
        // agota el reactivo a media manana, esperar al dia siguiente es
        // esperar demasiado.
        verify(eventProducer).publicarStockBajo(any(Reagent.class), eq(3), eq("req-2"));
    }

    @Test
    @DisplayName("El aviso usa el total del reactivo, no la cantidad del lote")
    void elAvisoUsaElTotalDelReactivo() {
        // El lote queda con 2 unidades, pero hay otros lotes del mismo
        // reactivo: el total disponible es 40 y no hay nada que avisar.
        Stock lote = lote(5, LocalDate.now().plusMonths(6), 10);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(40);

        movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, 3, null, null),
                "tecnico1", "req-3");

        verify(eventProducer, never()).publicarStockBajo(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("No se consume de un lote vencido")
    void noSeConsumeDeUnLoteVencido() {
        Stock lote = lote(20, LocalDate.now().minusDays(1), 5);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));

        assertThatThrownBy(() -> movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, 3, null, null), "tecnico1", "req-4"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("no se puede consumir");

        assertThat(lote.getCantidadActual()).isEqualTo(20);
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un lote vencido si admite merma: es lo que toca hacer con el")
    void unLoteVencidoAdmiteMerma() {
        Stock lote = lote(20, LocalDate.now().minusDays(1), 5);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(0);

        var respuesta = movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.MERMA, 20, "Lote vencido", null),
                "tecnico1", "req-5");

        assertThat(lote.getCantidadActual()).isZero();
        assertThat(respuesta.cantidad()).isEqualTo(-20);
        assertThat(respuesta.motivo()).isEqualTo("Lote vencido");
    }

    @Test
    @DisplayName("No se registra ningun movimiento sobre un lote retirado")
    void noSeRegistraSobreUnLoteRetirado() {
        Stock lote = lote(20, LocalDate.now().plusMonths(6), 5);
        lote.retirar("Deterioro por cadena de frio");
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));

        assertThatThrownBy(() -> movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, 1, null, null), "tecnico1", "req-6"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("esta retirado del almacen");
    }

    @Test
    @DisplayName("Una merma sin motivo se rechaza")
    void unaMermaSinMotivoSeRechaza() {
        // Una merma sin motivo no sirve para auditar nada.
        assertThatThrownBy(() -> movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.MERMA, 5, "  ", null), "tecnico1", "req-7"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("requiere indicar el motivo");

        // Ni se llega a tocar el lote.
        verify(stockRepository, never()).buscarParaMover(anyLong());
    }

    @Test
    @DisplayName("Una salida con cantidad negativa se rechaza")
    void unaSalidaConCantidadNegativaSeRechaza() {
        // El signo lo pone el tipo. Una cantidad negativa indica que quien
        // llama entendio mal la operacion.
        assertThatThrownBy(() -> movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, -5, null, null), "tecnico1", "req-8"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("Use AJUSTE para corregir a la baja");
    }

    @Test
    @DisplayName("Un ajuste si admite cantidad negativa")
    void unAjusteAdmiteCantidadNegativa() {
        Stock lote = lote(20, LocalDate.now().plusMonths(6), 5);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(18);

        var respuesta = movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.AJUSTE, -2, "Recuento fisico", null),
                "supervisor", "req-9");

        assertThat(lote.getCantidadActual()).isEqualTo(18);
        assertThat(respuesta.cantidad()).isEqualTo(-2);
    }

    @Test
    @DisplayName("Un movimiento de cero unidades se rechaza")
    void unMovimientoDeCeroSeRechaza() {
        assertThatThrownBy(() -> movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.AJUSTE, 0, "Sin cambio", null),
                "tecnico1", "req-10"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("debe ser distinta de cero");
    }

    @Test
    @DisplayName("Una salida mayor que el lote se rechaza con el detalle")
    void unaSalidaMayorQueElLoteSeRechaza() {
        Stock lote = lote(4, LocalDate.now().plusMonths(6), 1);
        when(stockRepository.buscarParaMover(ID_LOTE)).thenReturn(Optional.of(lote));

        assertThatThrownBy(() -> movementService.registrar(ID_LOTE,
                new MovementRequest(MovementType.SALIDA, 9, null, null), "tecnico1", "req-11"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("solo tiene 4 unidades y se solicitan 9");

        assertThat(lote.getCantidadActual()).isEqualTo(4);
    }

    @Test
    @DisplayName("El consumo reparte entre lotes empezando por el que caduca antes")
    void elConsumoRepartePorVencimiento() {
        Stock caduca = lote(4, LocalDate.now().plusDays(10), 1);
        ReflectionTestUtils.setField(caduca, "id", 70L);
        ReflectionTestUtils.setField(caduca, "lote", "L-VIEJO");

        Stock nuevo = lote(50, LocalDate.now().plusMonths(8), 1);
        ReflectionTestUtils.setField(nuevo, "id", 71L);
        ReflectionTestUtils.setField(nuevo, "lote", "L-NUEVO");

        when(stockRepository.lotesConsumibles(ID_REACTIVO)).thenReturn(List.of(caduca, nuevo));
        when(stockRepository.buscarParaMover(70L)).thenReturn(Optional.of(caduca));
        when(stockRepository.buscarParaMover(71L)).thenReturn(Optional.of(nuevo));
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(48);

        var movimientos = movementService.consumir(ID_REACTIVO, 6, "MUE-20261029-0002",
                "tecnico1", "req-12");

        // Agota el que caduca antes y toma el resto del siguiente.
        assertThat(movimientos).hasSize(2);
        assertThat(caduca.getCantidadActual()).isZero();
        assertThat(nuevo.getCantidadActual()).isEqualTo(48);
        assertThat(movimientos.get(0).lote()).isEqualTo("L-VIEJO");
        assertThat(movimientos.get(0).cantidad()).isEqualTo(-4);
        assertThat(movimientos.get(1).lote()).isEqualTo("L-NUEVO");
        assertThat(movimientos.get(1).cantidad()).isEqualTo(-2);
    }

    @Test
    @DisplayName("Si el total no alcanza, el consumo no toca ningun lote")
    void siElTotalNoAlcanzaNoConsumeNada() {
        Stock lote = lote(4, LocalDate.now().plusMonths(6), 1);
        ReflectionTestUtils.setField(lote, "id", 70L);
        when(stockRepository.lotesConsumibles(ID_REACTIVO)).thenReturn(List.of(lote));

        // Un consumo a medias dejaria material apuntado como gastado sin haber
        // podido hacer la prueba.
        assertThatThrownBy(() -> movementService.consumir(ID_REACTIVO, 10, "MUE-X",
                "tecnico1", "req-13"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("No hay 10 unidades disponibles");

        assertThat(lote.getCantidadActual()).isEqualTo(4);
        verify(movementRepository, never()).save(any());
        verify(stockRepository, never()).buscarParaMover(anyLong());
    }

    @Test
    @DisplayName("Un consumo sin ningun lote disponible se rechaza")
    void unConsumoSinLotesSeRechaza() {
        when(stockRepository.lotesConsumibles(ID_REACTIVO)).thenReturn(List.of());

        assertThatThrownBy(() -> movementService.consumir(ID_REACTIVO, 1, null,
                "tecnico1", "req-14"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("No hay 1 unidades disponibles");
    }

    @Test
    @DisplayName("El consumo exige cantidad positiva")
    void elConsumoExigeCantidadPositiva() {
        assertThatThrownBy(() -> movementService.consumir(ID_REACTIVO, 0, null,
                "tecnico1", "req-15"))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("debe ser distinta de cero");

        verify(stockRepository, never()).lotesConsumibles(anyLong());
    }

    @Test
    @DisplayName("Cada lote del consumo queda con su propio movimiento")
    void cadaLoteDelConsumoDejaSuMovimiento() {
        Stock primero = lote(2, LocalDate.now().plusDays(5), 1);
        ReflectionTestUtils.setField(primero, "id", 70L);
        Stock segundo = lote(2, LocalDate.now().plusDays(20), 1);
        ReflectionTestUtils.setField(segundo, "id", 71L);
        Stock tercero = lote(2, LocalDate.now().plusDays(40), 1);
        ReflectionTestUtils.setField(tercero, "id", 72L);

        when(stockRepository.lotesConsumibles(ID_REACTIVO))
                .thenReturn(List.of(primero, segundo, tercero));
        when(stockRepository.buscarParaMover(70L)).thenReturn(Optional.of(primero));
        when(stockRepository.buscarParaMover(71L)).thenReturn(Optional.of(segundo));
        when(stockRepository.buscarParaMover(72L)).thenReturn(Optional.of(tercero));
        when(stockRepository.totalDisponibleDe(ID_REACTIVO)).thenReturn(1);

        movementService.consumir(ID_REACTIVO, 5, "MUE-20261029-0003", "tecnico1", "req-16");

        // Tres movimientos, uno por lote, y el tercero solo con lo que faltaba.
        ArgumentCaptor<MovementLog> capturados = ArgumentCaptor.forClass(MovementLog.class);
        verify(movementRepository, times(3)).save(capturados.capture());
        assertThat(capturados.getAllValues())
                .extracting(MovementLog::getCantidad)
                .containsExactly(-2, -2, -1);
    }

    // ---- Datos de prueba ---------------------------------------------------

    private Stock lote(int cantidad, LocalDate vencimiento, int stockMinimo) {
        Reagent reactivo = new Reagent(
                "REA-HEM-01", "Reactivo de hemoglobina", "Hematologia", "frasco",
                "Wiener Lab", stockMinimo, 30, true, null);
        ReflectionTestUtils.setField(reactivo, "id", ID_REACTIVO);

        Stock lote = new Stock(reactivo, "L-2026-001", cantidad, vencimiento,
                LocalDate.now().minusDays(1), "Estanteria A", null);
        ReflectionTestUtils.setField(lote, "id", ID_LOTE);
        return lote;
    }
}
