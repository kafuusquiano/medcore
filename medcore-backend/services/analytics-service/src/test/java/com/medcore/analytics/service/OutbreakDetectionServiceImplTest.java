package com.medcore.analytics.service;

import com.medcore.analytics.config.AnalyticsProperties;
import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.domain.document.OutbreakAlert;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.RiskLevel;
import com.medcore.analytics.exception.AnalyticsException;
import com.medcore.analytics.repository.AnalyticsRecordRepository;
import com.medcore.analytics.repository.OutbreakAlertRepository;
import com.medcore.analytics.repository.projection.ConteoPorZonaYPrueba;
import com.medcore.analytics.service.impl.OutbreakDetectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la deteccion de brotes.
 *
 * <p>Lo que se verifica aqui es que las reglas que hacen util la vigilancia se
 * cumplan: contar pacientes y no resultados, no duplicar alertas del mismo
 * brote, y que un fallo en una zona no deje sin vigilar las demas.
 */
@ExtendWith(MockitoExtension.class)
class OutbreakDetectionServiceImplTest {

    private static final int UMBRAL = 5;
    private static final int VENTANA = 7;
    private static final String ZONA = "SAN JUAN DE LURIGANCHO";
    private static final String PRUEBA = "PCR-001";

    @Mock
    private AnalyticsRecordRepository recordRepository;

    @Mock
    private OutbreakAlertRepository alertRepository;

    private OutbreakDetectionServiceImpl detectionService;

    @BeforeEach
    void prepararServicio() {
        detectionService = new OutbreakDetectionServiceImpl(
                recordRepository, alertRepository,
                new AnalyticsProperties(
                        new AnalyticsProperties.Brote(UMBRAL, VENTANA), "0 0 * * * *"));

        lenient().when(alertRepository.save(any(OutbreakAlert.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("Un barrido sin candidatos no abre ninguna alerta")
    void unBarridoSinCandidatosNoAbreNada() {
        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of());

        assertThat(detectionService.detectar("req-1")).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Abre una alerta cuando los casos llegan al umbral")
    void abreUnaAlertaAlLlegarAlUmbral() {
        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of(conteo(5, 0)));
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado(ZONA, PRUEBA, AlertStatus.ABIERTA))
                .thenReturn(Optional.empty());

        var alertas = detectionService.detectar("req-2");

        assertThat(alertas).hasSize(1);
        OutbreakAlert alerta = alertas.get(0);
        assertThat(alerta.zona()).isEqualTo(ZONA);
        assertThat(alerta.casos()).isEqualTo(5);
        assertThat(alerta.umbral()).isEqualTo(UMBRAL);
        assertThat(alerta.ventanaDias()).isEqualTo(VENTANA);
        assertThat(alerta.estado()).isEqualTo(AlertStatus.ABIERTA);
        // Justo en el umbral y sin criticos: riesgo bajo.
        assertThat(alerta.nivelRiesgo()).isEqualTo(RiskLevel.BAJO);
    }

    @Test
    @DisplayName("No abre una segunda alerta del mismo brote: actualiza la abierta")
    void noDuplicaLaAlertaDelMismoBrote() {
        OutbreakAlert yaAbierta = OutbreakAlert.abrir(
                ZONA, PRUEBA, "Proteina C reactiva", 5, 0, UMBRAL, VENTANA,
                List.of("1", "2", "3", "4", "5"),
                LocalDate.now().minusDays(3), LocalDate.now().minusDays(1));

        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of(conteo(8, 1)));
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado(ZONA, PRUEBA, AlertStatus.ABIERTA))
                .thenReturn(Optional.of(yaAbierta));

        var alertas = detectionService.detectar("req-3");

        // Sin esta regla, cada caso nuevo del mismo brote abriria una alerta y
        // el panel acabaria con veinte avisos del mismo hecho.
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).casos()).isEqualTo(8);
        // Un caso critico sube el riesgo a MEDIO.
        assertThat(alertas.get(0).nivelRiesgo()).isEqualTo(RiskLevel.MEDIO);
        // Conserva la deteccion original: interesa cuando se detecto el brote,
        // no cuando se recalculo.
        assertThat(alertas.get(0).fechaDeteccion()).isEqualTo(yaAbierta.fechaDeteccion());
    }

    @Test
    @DisplayName("No informa de movimiento si las cifras no cambiaron")
    void noInformaSiLasCifrasNoCambiaron() {
        OutbreakAlert yaAbierta = OutbreakAlert.abrir(
                ZONA, PRUEBA, "Proteina C reactiva", 6, 1, UMBRAL, VENTANA,
                List.of("1", "2", "3", "4", "5", "6"),
                LocalDate.now().minusDays(3), LocalDate.now());

        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of(conteo(6, 1)));
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado(ZONA, PRUEBA, AlertStatus.ABIERTA))
                .thenReturn(Optional.of(yaAbierta));

        // El barrido corre cada hora: informar de una alerta que no se movio
        // llenaria el log de ruido.
        assertThat(detectionService.detectar("req-4")).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una carrera con otra ejecucion no es un error")
    void unaCarreraNoEsUnError() {
        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of(conteo(5, 0)));
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado(ZONA, PRUEBA, AlertStatus.ABIERTA))
                .thenReturn(Optional.empty());
        // El indice unico parcial de la coleccion rechaza la segunda alerta
        // abierta: ocurre si el scheduler y el consumidor evaluan lo mismo a
        // la vez.
        doThrow(new DuplicateKeyException("alerta_abierta"))
                .when(alertRepository).save(any(OutbreakAlert.class));

        assertThat(detectionService.detectar("req-5")).isEmpty();
    }

    @Test
    @DisplayName("Un fallo en una zona no deja sin vigilar las demas")
    void unFalloEnUnaZonaNoCortaElBarrido() {
        ConteoPorZonaYPrueba primera = conteo("COMAS", 6, 0);
        ConteoPorZonaYPrueba segunda = conteo("ATE", 7, 0);
        ConteoPorZonaYPrueba tercera = conteo("CALLAO", 8, 0);

        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of(primera, segunda, tercera));
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        // La segunda zona falla al consultar su alerta.
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado("ATE", PRUEBA, AlertStatus.ABIERTA))
                .thenThrow(new IllegalStateException("mongo inaccesible"));

        var alertas = detectionService.detectar("req-6");

        // Un barrido que se corta en la segunda zona deja sin vigilar las
        // veinte siguientes.
        assertThat(alertas).hasSize(2);
        assertThat(alertas).extracting(OutbreakAlert::zona)
                .containsExactly("COMAS", "CALLAO");
    }

    @Test
    @DisplayName("La evaluacion puntual cuenta PACIENTES, no resultados")
    void laEvaluacionCuentaPacientesNoResultados() {
        // Seis resultados anormales, pero de solo tres personas: a una a la
        // que se repite la prueba no se la puede contar tres veces, o
        // cualquier seguimiento pareceria un brote.
        when(recordRepository.casosAnormalesDe(
                any(), any(), any(), any()))
                .thenReturn(List.of(
                        registro("11111111"), registro("11111111"),
                        registro("22222222"), registro("22222222"),
                        registro("33333333"), registro("33333333")));

        var alertas = detectionService.evaluar(ZONA, PRUEBA, "req-7");

        assertThat(alertas).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("La evaluacion puntual abre la alerta con pacientes distintos suficientes")
    void laEvaluacionAbreConPacientesSuficientes() {
        when(recordRepository.casosAnormalesDe(
                any(), any(), any(), any()))
                .thenReturn(List.of(
                        registro("11111111"), registro("22222222"), registro("33333333"),
                        registro("44444444"), registro("55555555")));
        when(alertRepository.findByZonaAndCodigoPruebaAndEstado(ZONA, PRUEBA, AlertStatus.ABIERTA))
                .thenReturn(Optional.empty());

        var alertas = detectionService.evaluar(ZONA, PRUEBA, "req-8");

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).casos()).isEqualTo(5);
        assertThat(alertas.get(0).dnisAfectados()).hasSize(5);
    }

    @Test
    @DisplayName("Cierra una alerta con su motivo")
    void cierraUnaAlertaConSuMotivo() {
        OutbreakAlert abierta = OutbreakAlert.abrir(
                ZONA, PRUEBA, "Proteina C reactiva", 6, 0, UMBRAL, VENTANA,
                List.of("1"), LocalDate.now().minusDays(2), LocalDate.now());
        when(alertRepository.findById("a1")).thenReturn(Optional.of(abierta));

        var resuelta = detectionService.resolver(
                "a1", AlertStatus.DESCARTADA, "epidemiologo1",
                "Cribado programado en la zona, no es un brote");

        assertThat(resuelta.estado()).isEqualTo(AlertStatus.DESCARTADA);
        assertThat(resuelta.resueltaPor()).isEqualTo("epidemiologo1");
        assertThat(resuelta.motivoResolucion()).contains("Cribado programado");
        assertThat(resuelta.fechaResolucion()).isNotNull();
    }

    @Test
    @DisplayName("No cierra una alerta sin motivo")
    void noCierraSinMotivo() {
        // Una alerta cerrada sin explicacion no sirve para revisar despues si
        // el umbral estaba bien puesto.
        assertThatThrownBy(() -> detectionService.resolver(
                "a1", AlertStatus.DESCARTADA, "epidemiologo1", "  "))
                .isInstanceOf(AnalyticsException.class)
                .hasMessageContaining("Indique por que se cierra");

        verify(alertRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Una alerta ya cerrada no se reabre")
    void unaAlertaCerradaNoSeReabre() {
        OutbreakAlert cerrada = OutbreakAlert.abrir(
                        ZONA, PRUEBA, "Proteina C reactiva", 6, 0, UMBRAL, VENTANA,
                        List.of("1"), LocalDate.now().minusDays(2), LocalDate.now())
                .resolver(AlertStatus.CONFIRMADA, "epidemiologo1", "Brote confirmado");
        when(alertRepository.findById("a1")).thenReturn(Optional.of(cerrada));

        assertThatThrownBy(() -> detectionService.resolver(
                "a1", AlertStatus.DESCARTADA, "otro", "Me equivoque"))
                .isInstanceOf(AnalyticsException.class)
                .hasMessageContaining("se abrira una alerta nueva");
    }

    @Test
    @DisplayName("Una alerta que no existe devuelve 404")
    void unaAlertaQueNoExisteDevuelve404() {
        when(alertRepository.findById("nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> detectionService.obtenerPorId("nada"))
                .isInstanceOf(AnalyticsException.class)
                .hasMessageContaining("No existe la alerta");
    }

    @Test
    @DisplayName("El nivel de riesgo sube con los casos y con los criticos")
    void elNivelDeRiesgoSubeConCasosYCriticos() {
        // En el umbral, sin criticos.
        assertThat(RiskLevel.evaluar(5, 5, 0)).isEqualTo(RiskLevel.BAJO);
        // El doble del umbral.
        assertThat(RiskLevel.evaluar(10, 5, 0)).isEqualTo(RiskLevel.MEDIO);
        // El triple.
        assertThat(RiskLevel.evaluar(15, 5, 0)).isEqualTo(RiskLevel.ALTO);
        // Un solo critico ya sube a MEDIO aunque los casos apenas superen el
        // umbral: hay alguien en riesgo inmediato, y eso pesa mas que el
        // recuento.
        assertThat(RiskLevel.evaluar(5, 5, 1)).isEqualTo(RiskLevel.MEDIO);
        assertThat(RiskLevel.evaluar(6, 5, 3)).isEqualTo(RiskLevel.ALTO);
        assertThat(RiskLevel.ALTO.exigeAvisoInmediato()).isTrue();
        assertThat(RiskLevel.MEDIO.exigeAvisoInmediato()).isFalse();
    }

    @Test
    @DisplayName("Los umbrales invalidos de la configuracion caen al valor por omision")
    void losUmbralesInvalidosCaenAlValorPorOmision() {
        // Sin este respaldo, un arranque con el config-server a medias dejaria
        // el umbral en null y la deteccion fallaria con NullPointerException
        // en lugar de vigilar. Un umbral de 1 convertiria cada caso anormal en
        // un brote.
        assertThat(new AnalyticsProperties(null, null).umbralCasos()).isEqualTo(5);
        assertThat(new AnalyticsProperties(
                new AnalyticsProperties.Brote(null, null), null).umbralCasos()).isEqualTo(5);
        assertThat(new AnalyticsProperties(
                new AnalyticsProperties.Brote(1, 7), null).umbralCasos()).isEqualTo(5);
        assertThat(new AnalyticsProperties(
                new AnalyticsProperties.Brote(3, 0), null).ventanaDias()).isEqualTo(7);
        assertThat(new AnalyticsProperties(
                new AnalyticsProperties.Brote(3, 14), null).umbralCasos()).isEqualTo(3);
    }

    @Test
    @DisplayName("El resumen cuenta las alertas abiertas por nivel")
    void elResumenCuentaPorNivel() {
        when(alertRepository.countByEstado(AlertStatus.ABIERTA)).thenReturn(6L);
        when(alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.ALTO))
                .thenReturn(1L);
        when(alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.MEDIO))
                .thenReturn(2L);
        when(alertRepository.countByEstadoAndNivelRiesgo(AlertStatus.ABIERTA, RiskLevel.BAJO))
                .thenReturn(3L);

        var resumen = detectionService.resumen();

        assertThat(resumen.abiertas()).isEqualTo(6);
        assertThat(resumen.riesgoAlto()).isEqualTo(1);
        assertThat(resumen.riesgoMedio()).isEqualTo(2);
        assertThat(resumen.riesgoBajo()).isEqualTo(3);
    }

    @Test
    @DisplayName("El barrido pasa a Mongo el umbral configurado")
    void elBarridoPasaElUmbralConfigurado() {
        when(recordRepository.casosPorZonaYPrueba(any(), any(), anyInt()))
                .thenReturn(List.of());

        detectionService.detectar("req-9");

        // El umbral se aplica en la agregacion y no filtrando en memoria: asi
        // Mongo descarta de entrada las combinaciones que no llegan.
        ArgumentCaptor<Integer> umbral = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDate> desde = ArgumentCaptor.forClass(LocalDate.class);
        verify(recordRepository, times(1))
                .casosPorZonaYPrueba(desde.capture(), any(), umbral.capture());

        assertThat(umbral.getValue()).isEqualTo(UMBRAL);
        // La ventana son 7 dias contando hoy, asi que empieza hace 6.
        assertThat(desde.getValue()).isEqualTo(LocalDate.now().minusDays(VENTANA - 1L));
    }

    // ---- Datos de prueba ---------------------------------------------------

    private ConteoPorZonaYPrueba conteo(int casos, int criticos) {
        return conteo(ZONA, casos, criticos);
    }

    private ConteoPorZonaYPrueba conteo(String zona, int casos, int criticos) {
        return new ConteoPorZonaYPrueba(
                zona, PRUEBA, "Proteina C reactiva", casos, criticos,
                List.of("11111111", "22222222", "33333333", "44444444", "55555555"),
                LocalDate.now().minusDays(4), LocalDate.now());
    }

    private AnalyticsRecord registro(String dni) {
        return AnalyticsRecord.de(
                (long) dni.hashCode(), "MUE-20261029-0001", 1L, dni, ZONA,
                PRUEBA, "Proteina C reactiva", 48.5, "mg/L", "ALTO", false,
                Instant.now(), ZoneId.systemDefault());
    }
}
