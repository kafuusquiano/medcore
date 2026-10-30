package com.medcore.inventory.domain;

import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.enums.MovementType;
import com.medcore.inventory.domain.enums.StockStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de las reglas que viven en el dominio.
 *
 * <p>Se prueban sin mocks porque no dependen de nada externo: son el calculo
 * de la situacion de un lote y el signo de un movimiento, y son justamente
 * las decisiones que no deben quedar repartidas por los servicios.
 */
class StockRulesTest {

    private static final int DIAS_AVISO = 30;

    @Test
    @DisplayName("Un lote con cantidad suficiente y lejos de vencer esta DISPONIBLE")
    void unLoteConCantidadSuficienteEstaDisponible() {
        Stock lote = lote(50, LocalDate.now().plusMonths(6), 10);

        assertThat(lote.situacion(DIAS_AVISO)).isEqualTo(StockStatus.DISPONIBLE);
        assertThat(lote.esConsumible()).isTrue();
        assertThat(lote.situacion(DIAS_AVISO).requiereAtencion()).isFalse();
    }

    @Test
    @DisplayName("Un lote vencido lo esta aunque le queden unidades")
    void unLoteVencidoLoEstaAunqueTengaUnidades() {
        Stock lote = lote(50, LocalDate.now().minusDays(1), 10);

        // El orden de comprobacion importa: vencido manda sobre todo lo demas,
        // porque usar ese material daria un resultado que no se puede
        // respaldar.
        assertThat(lote.situacion(DIAS_AVISO)).isEqualTo(StockStatus.VENCIDO);
        assertThat(lote.esConsumible()).isFalse();
        assertThat(lote.situacion(DIAS_AVISO).esUsable()).isFalse();
        assertThat(lote.diasHastaVencimiento()).isNegative();
    }

    @Test
    @DisplayName("Un lote agotado no figura como bajo minimo")
    void unLoteAgotadoNoFiguraComoBajoMinimo() {
        Stock lote = lote(0, LocalDate.now().plusMonths(6), 10);

        // Agotado es mas informativo que bajo: no es que quede poco, es que no
        // queda nada.
        assertThat(lote.situacion(DIAS_AVISO)).isEqualTo(StockStatus.AGOTADO);
        assertThat(lote.esConsumible()).isFalse();
    }

    @Test
    @DisplayName("Un lote dentro del plazo de aviso esta POR_VENCER y sigue siendo usable")
    void unLotePorVencerSigueSiendoUsable() {
        Stock lote = lote(50, LocalDate.now().plusDays(10), 10);

        assertThat(lote.situacion(DIAS_AVISO)).isEqualTo(StockStatus.POR_VENCER);
        // Es un aviso para gastarlo primero, no un impedimento: un lote por
        // vencer es justamente el que conviene usar ya.
        assertThat(lote.situacion(DIAS_AVISO).esUsable()).isTrue();
        assertThat(lote.esConsumible()).isTrue();
    }

    @Test
    @DisplayName("El plazo de aviso cambia la situacion del mismo lote")
    void elPlazoDeAvisoCambiaLaSituacion() {
        Stock lote = lote(50, LocalDate.now().plusDays(45), 10);

        // Con 30 dias de aviso todavia no entra; con 60 si. Por eso el plazo
        // es configuracion y no una constante del codigo.
        assertThat(lote.situacion(30)).isEqualTo(StockStatus.DISPONIBLE);
        assertThat(lote.situacion(60)).isEqualTo(StockStatus.POR_VENCER);
    }

    @Test
    @DisplayName("Un lote por debajo del minimo del reactivo esta BAJO")
    void unLoteBajoElMinimoEstaBajo() {
        Stock lote = lote(3, LocalDate.now().plusMonths(6), 10);

        assertThat(lote.situacion(DIAS_AVISO)).isEqualTo(StockStatus.BAJO);
        assertThat(lote.esConsumible()).isTrue();
    }

    @Test
    @DisplayName("Un insumo sin fecha de vencimiento nunca vence")
    void unInsumoSinVencimientoNuncaVence() {
        // Gradillas, puntas de pipeta: no caducan.
        Stock lote = lote(50, null, 10);

        assertThat(lote.estaVencido()).isFalse();
        assertThat(lote.venceEnMenosDe(3650)).isFalse();
        assertThat(lote.diasHastaVencimiento()).isNull();
        assertThat(lote.situacion(DIAS_AVISO)).isEqualTo(StockStatus.DISPONIBLE);
    }

    @Test
    @DisplayName("Un movimiento no puede dejar el lote en negativo")
    void unMovimientoNoPuedeDejarElLoteEnNegativo() {
        Stock lote = lote(5, LocalDate.now().plusMonths(6), 1);

        assertThatThrownBy(() -> lote.aplicarMovimiento(-8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solo tiene 5 unidades");

        // La cantidad no cambia tras el intento fallido.
        assertThat(lote.getCantidadActual()).isEqualTo(5);
    }

    @Test
    @DisplayName("Los movimientos suman y restan segun su tipo")
    void losMovimientosSumanYRestanSegunSuTipo() {
        Stock lote = lote(10, LocalDate.now().plusMonths(6), 1);

        lote.aplicarMovimiento(MovementType.ENTRADA.deltaDe(5));
        assertThat(lote.getCantidadActual()).isEqualTo(15);

        lote.aplicarMovimiento(MovementType.SALIDA.deltaDe(3));
        assertThat(lote.getCantidadActual()).isEqualTo(12);

        lote.aplicarMovimiento(MovementType.MERMA.deltaDe(2));
        assertThat(lote.getCantidadActual()).isEqualTo(10);
    }

    @Test
    @DisplayName("El signo lo pone el tipo, no quien llama")
    void elSignoLoPoneElTipo() {
        // Enviar la cantidad en negativo no debe invertir el movimiento: una
        // salida resta aunque llegue como -3, para que un cliente descuidado
        // no acabe sumando material inventado.
        assertThat(MovementType.SALIDA.deltaDe(3)).isEqualTo(-3);
        assertThat(MovementType.SALIDA.deltaDe(-3)).isEqualTo(-3);
        assertThat(MovementType.ENTRADA.deltaDe(3)).isEqualTo(3);
        assertThat(MovementType.ENTRADA.deltaDe(-3)).isEqualTo(3);
    }

    @Test
    @DisplayName("Solo el AJUSTE conserva el signo que le llega")
    void soloElAjusteConservaElSigno() {
        // Un ajuste corrige en cualquier direccion tras un recuento fisico.
        assertThat(MovementType.AJUSTE.deltaDe(4)).isEqualTo(4);
        assertThat(MovementType.AJUSTE.deltaDe(-4)).isEqualTo(-4);
        assertThat(MovementType.AJUSTE.admiteCantidadNegativa()).isTrue();
        assertThat(MovementType.SALIDA.admiteCantidadNegativa()).isFalse();
    }

    @Test
    @DisplayName("Retirar un lote no pone su cantidad a cero")
    void retirarNoPoneLaCantidadACero() {
        Stock lote = lote(7, LocalDate.now().minusDays(2), 1);

        lote.retirar("Vencido en la revision diaria");

        // La cantidad se da de baja con una MERMA, que deja constancia de
        // cuantas unidades se perdieron. Separarlo permite saber si un lote se
        // retiro lleno o ya casi gastado.
        assertThat(lote.isRetirado()).isTrue();
        assertThat(lote.getCantidadActual()).isEqualTo(7);
        assertThat(lote.esConsumible()).isFalse();
        assertThat(lote.getObservaciones()).contains("Retirado: Vencido en la revision diaria");
    }

    @Test
    @DisplayName("Las unidades consumidas son la diferencia con la cantidad inicial")
    void lasUnidadesConsumidasSonLaDiferencia() {
        Stock lote = lote(30, LocalDate.now().plusMonths(6), 1);
        lote.aplicarMovimiento(MovementType.SALIDA.deltaDe(12));

        assertThat(lote.getCantidadInicial()).isEqualTo(30);
        assertThat(lote.getCantidadActual()).isEqualTo(18);
        assertThat(lote.unidadesConsumidas()).isEqualTo(12);
    }

    @Test
    @DisplayName("La cantidad sugerida de pedido lleva al stock optimo")
    void laCantidadSugeridaLlevaAlOptimo() {
        Reagent reactivo = reactivo(10, 30);

        assertThat(reactivo.cantidadSugeridaDePedido(4)).isEqualTo(26);
        assertThat(reactivo.estaBajoMinimo(4)).isTrue();

        // Ya en el optimo o por encima: no hay nada que pedir.
        assertThat(reactivo.cantidadSugeridaDePedido(30)).isZero();
        assertThat(reactivo.cantidadSugeridaDePedido(45)).isZero();
        assertThat(reactivo.estaBajoMinimo(30)).isFalse();
    }

    @Test
    @DisplayName("Sin stock optimo definido no se sugiere cantidad")
    void sinOptimoNoSeSugiereCantidad() {
        Reagent reactivo = reactivo(10, null);

        // Bajo minimo si, pero el sistema no inventa cuanto pedir: esa
        // decision es de quien hace el pedido.
        assertThat(reactivo.estaBajoMinimo(4)).isTrue();
        assertThat(reactivo.cantidadSugeridaDePedido(4)).isZero();
    }

    // ---- Datos de prueba ---------------------------------------------------

    private Stock lote(int cantidad, LocalDate vencimiento, int stockMinimo) {
        return new Stock(
                reactivo(stockMinimo, null),
                "L-2026-001",
                cantidad,
                vencimiento,
                LocalDate.now().minusDays(1),
                "Estanteria A",
                null);
    }

    private Reagent reactivo(int stockMinimo, Integer stockOptimo) {
        return new Reagent(
                "REA-HEM-01", "Reactivo de hemoglobina", "Hematologia", "frasco",
                "Wiener Lab", stockMinimo, stockOptimo, true, null);
    }
}
