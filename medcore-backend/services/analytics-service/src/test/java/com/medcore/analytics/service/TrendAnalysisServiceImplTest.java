package com.medcore.analytics.service;

import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.repository.AnalyticsRecordRepository;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.analytics.service.impl.TrendAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendAnalysisServiceImplTest {

    @Mock
    private AnalyticsRecordRepository recordRepository;

    private TrendAnalysisServiceImpl trendService;

    @BeforeEach
    void prepararServicio() {
        trendService = new TrendAnalysisServiceImpl(recordRepository);
    }

    @Test
    @DisplayName("Los dias sin datos salen con ceros, no se omiten")
    void losDiasSinDatosSalenConCeros() {
        LocalDate hoy = LocalDate.now();

        // Solo dos dias con datos dentro de una semana.
        when(recordRepository.serieDiaria(any(), any()))
                .thenReturn(List.of(
                        new ConteoPorFecha(hoy.minusDays(5), 10, 4, 1),
                        new ConteoPorFecha(hoy, 8, 2, 0)));

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.SEMANAL);

        // Siete puntos, uno por dia: una grafica que salta del dia 3 al 9
        // dibuja una recta entre ambos y sugiere una tendencia que no existe.
        assertThat(tendencia.puntos()).hasSize(7);
        assertThat(tendencia.puntos()).allSatisfy(punto ->
                assertThat(punto.dias()).isEqualTo(1));
        assertThat(tendencia.totalResultados()).isEqualTo(18);
        assertThat(tendencia.totalCasos()).isEqualTo(6);
        assertThat(tendencia.totalCriticos()).isEqualTo(1);

        // Los dias intermedios estan y valen cero.
        long enCero = tendencia.puntos().stream()
                .filter(punto -> punto.resultados() == 0)
                .count();
        assertThat(enCero).isEqualTo(5);
    }

    @Test
    @DisplayName("Una serie anual agrupa por mes para no devolver 365 puntos")
    void unaSerieAnualAgrupaPorMes() {
        when(recordRepository.serieDiaria(any(), any())).thenReturn(List.of());

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.ANUAL);

        // 365 dias en tramos de 30: 13 puntos, el ultimo mas corto. Una
        // grafica con un punto por dia no se lee.
        assertThat(tendencia.puntos()).hasSize(13);
        assertThat(tendencia.puntos().get(0).dias()).isEqualTo(30);
        assertThat(tendencia.periodo()).isEqualTo(ReportPeriod.ANUAL);
    }

    @Test
    @DisplayName("Un trimestre agrupa por semana")
    void unTrimestreAgrupaPorSemana() {
        when(recordRepository.serieDiaria(any(), any())).thenReturn(List.of());

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.TRIMESTRAL);

        assertThat(tendencia.puntos()).hasSize(13);
        assertThat(tendencia.puntos().get(0).dias()).isEqualTo(7);
    }

    @Test
    @DisplayName("La positividad es la proporcion de casos sobre resultados")
    void laPositividadEsLaProporcion() {
        LocalDate hoy = LocalDate.now();
        when(recordRepository.serieDiaria(any(), any()))
                .thenReturn(List.of(new ConteoPorFecha(hoy, 20, 5, 1)));

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.DIARIO);

        // 5 de 20 es el 25 por ciento. Es el indicador que se sigue: un pico
        // de casos puede ser solo un dia de mucho trabajo, uno de positividad
        // no.
        assertThat(tendencia.positividadMedia()).isEqualTo(25.0);
        assertThat(tendencia.puntos().get(0).positividad()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Sin resultados la positividad es cero y no un error de division")
    void sinResultadosLaPositividadEsCero() {
        when(recordRepository.serieDiaria(any(), any())).thenReturn(List.of());

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.SEMANAL);

        assertThat(tendencia.positividadMedia()).isZero();
        assertThat(tendencia.totalResultados()).isZero();
    }

    @Test
    @DisplayName("Compara con el periodo anterior de la misma duracion")
    void comparaConElPeriodoAnterior() {
        LocalDate hoy = LocalDate.now();
        LocalDate desdeActual = ReportPeriod.SEMANAL.desde(hoy);

        // La semana en curso: 10 casos. La anterior: 5.
        when(recordRepository.serieDiaria(desdeActual, hoy))
                .thenReturn(List.of(new ConteoPorFecha(hoy, 30, 10, 0)));
        LocalDate hastaAnterior = desdeActual.minusDays(1);
        when(recordRepository.serieDiaria(
                ReportPeriod.SEMANAL.desde(hastaAnterior), hastaAnterior))
                .thenReturn(List.of(new ConteoPorFecha(hastaAnterior, 20, 5, 0)));

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.SEMANAL);

        assertThat(tendencia.tendencia().casosPeriodoAnterior()).isEqualTo(5);
        assertThat(tendencia.tendencia().variacionPorcentual()).isEqualTo(100.0);
        assertThat(tendencia.tendencia().direccion()).isEqualTo("SUBE");
    }

    @Test
    @DisplayName("Sin casos antes, la variacion es nula y no un porcentaje inventado")
    void sinCasosAntesLaVariacionEsNula() {
        LocalDate hoy = LocalDate.now();
        LocalDate desdeActual = ReportPeriod.SEMANAL.desde(hoy);

        when(recordRepository.serieDiaria(desdeActual, hoy))
                .thenReturn(List.of(new ConteoPorFecha(hoy, 10, 5, 0)));
        LocalDate hastaAnterior = desdeActual.minusDays(1);
        when(recordRepository.serieDiaria(
                ReportPeriod.SEMANAL.desde(hastaAnterior), hastaAnterior))
                .thenReturn(List.of());

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.SEMANAL);

        // Pasar de 0 a 5 casos no es "subio un 500 por ciento": es que no
        // habia con que comparar. Un infinito o un numero inventado harian
        // que el panel mostrara una cifra sin sentido.
        assertThat(tendencia.tendencia().variacionPorcentual()).isNull();
        assertThat(tendencia.tendencia().casosPeriodoAnterior()).isZero();
        assertThat(tendencia.tendencia().direccion()).isEqualTo("SUBE");
    }

    @Test
    @DisplayName("Una variacion pequena se informa como ESTABLE")
    void unaVariacionPequenaEsEstable() {
        LocalDate hoy = LocalDate.now();
        LocalDate desdeActual = ReportPeriod.SEMANAL.desde(hoy);

        // De 100 a 102 casos: un 2 por ciento, ruido.
        when(recordRepository.serieDiaria(desdeActual, hoy))
                .thenReturn(List.of(new ConteoPorFecha(hoy, 300, 102, 0)));
        LocalDate hastaAnterior = desdeActual.minusDays(1);
        when(recordRepository.serieDiaria(
                ReportPeriod.SEMANAL.desde(hastaAnterior), hastaAnterior))
                .thenReturn(List.of(new ConteoPorFecha(hastaAnterior, 300, 100, 0)));

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.SEMANAL);

        assertThat(tendencia.tendencia().direccion()).isEqualTo("ESTABLE");
    }

    @Test
    @DisplayName("Una bajada se informa como BAJA")
    void unaBajadaSeInformaComoBaja() {
        LocalDate hoy = LocalDate.now();
        LocalDate desdeActual = ReportPeriod.SEMANAL.desde(hoy);

        when(recordRepository.serieDiaria(desdeActual, hoy))
                .thenReturn(List.of(new ConteoPorFecha(hoy, 100, 20, 0)));
        LocalDate hastaAnterior = desdeActual.minusDays(1);
        when(recordRepository.serieDiaria(
                ReportPeriod.SEMANAL.desde(hastaAnterior), hastaAnterior))
                .thenReturn(List.of(new ConteoPorFecha(hastaAnterior, 100, 50, 0)));

        var tendencia = trendService.tendenciaGeneral(ReportPeriod.SEMANAL);

        assertThat(tendencia.tendencia().direccion()).isEqualTo("BAJA");
        assertThat(tendencia.tendencia().variacionPorcentual()).isEqualTo(-60.0);
    }

    @Test
    @DisplayName("La serie de una zona lleva su filtro y usa la consulta filtrada")
    void laSerieDeUnaZonaUsaLaConsultaFiltrada() {
        LocalDate hoy = LocalDate.now();
        when(recordRepository.serieDiariaDeZona(any(), any(), any()))
                .thenReturn(List.of(new ConteoPorFecha(hoy, 5, 2, 0)));

        var tendencia = trendService.tendenciaDeZona("COMAS", ReportPeriod.SEMANAL);

        assertThat(tendencia.filtro()).isEqualTo("COMAS");
        // Los totales son del periodo en curso. El stub responde a las dos
        // llamadas, la del periodo actual y la del anterior, pero lo que suma
        // tendencia.totalCasos() es solo la ventana vigente.
        assertThat(tendencia.totalCasos()).isEqualTo(2);
        assertThat(tendencia.tendencia().casosPeriodoAnterior()).isEqualTo(2);
    }

    @Test
    @DisplayName("Las ventanas de los periodos son las esperadas")
    void lasVentanasDeLosPeriodosSonLasEsperadas() {
        LocalDate hasta = LocalDate.of(2026, 10, 29);

        // La ventana incluye el dia de hoy, asi que un periodo de 7 dias
        // empieza hace 6.
        assertThat(ReportPeriod.DIARIO.desde(hasta)).isEqualTo(hasta);
        assertThat(ReportPeriod.SEMANAL.desde(hasta)).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(ReportPeriod.MENSUAL.desde(hasta)).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(ReportPeriod.ANUAL.desde(hasta)).isEqualTo(LocalDate.of(2025, 10, 30));
    }
}
