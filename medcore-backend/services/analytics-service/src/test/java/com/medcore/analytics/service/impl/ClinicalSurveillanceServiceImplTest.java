package com.medcore.analytics.service.impl;

import com.medcore.analytics.domain.document.DiagnosisRecord;
import com.medcore.analytics.repository.DiagnosisRecordRepository;
import com.medcore.commons.event.DiagnosisEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalSurveillanceServiceImplTest {

    @Mock
    private DiagnosisRecordRepository repository;

    private ClinicalSurveillanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClinicalSurveillanceServiceImpl(repository);
    }

    @Test
    @DisplayName("Guarda el diagnostico con su grupo de edad y su zona")
    void guardaConGrupoYZona() {
        when(repository.findById("7")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.registrar(evento(7L, "A09", "San Juan de Lurigancho", 3, false));

        ArgumentCaptor<DiagnosisRecord> captor =
                ArgumentCaptor.forClass(DiagnosisRecord.class);
        verify(repository).save(captor.capture());

        DiagnosisRecord guardado = captor.getValue();
        assertThat(guardado.id()).isEqualTo("7");
        assertThat(guardado.codigoCie10()).isEqualTo("A09");
        assertThat(guardado.zona()).isEqualTo("San Juan de Lurigancho");
        // Tres anios cae en el tramo que concentra la mortalidad infantil, y
        // por eso no se mete en un tramo de diez anios.
        assertThat(guardado.grupoEdad()).isEqualTo("1 A 4");
    }

    @Test
    @DisplayName("Un reenvio del mismo evento no cuenta el caso dos veces")
    void elReenvioNoCuentaDosVeces() {
        DiagnosisRecord yaGuardado = DiagnosisRecord.de(
                evento(7L, "A09", "Comas", 30, false));
        when(repository.findById("7")).thenReturn(Optional.of(yaGuardado));

        DiagnosisRecord resultado = service.registrar(
                evento(7L, "A09", "Comas", 30, false));

        // Kafka entrega al menos una vez; un caso contado dos veces puede
        // disparar un brote que no existe.
        assertThat(resultado).isSameAs(yaGuardado);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Si dos consumidores guardan a la vez, no falla")
    void dosConsumidoresALaVez() {
        DiagnosisRecord elQueGano = DiagnosisRecord.de(
                evento(7L, "A09", "Comas", 30, false));

        when(repository.findById("7"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(elQueGano));
        when(repository.save(any())).thenThrow(new DuplicateKeyException("_id"));

        // El otro consumidor del grupo llego primero. No es un error: el caso
        // esta registrado, que es lo que importa.
        assertThat(service.registrar(evento(7L, "A09", "Comas", 30, false)))
                .isSameAs(elQueGano);
    }

    @Test
    @DisplayName("Una zona desconocida no se reparte entre distritos")
    void zonaDesconocidaNoSeReparte() {
        when(repository.findById("8")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.registrar(evento(8L, "J11", null, 45, false));

        ArgumentCaptor<DiagnosisRecord> captor =
                ArgumentCaptor.forClass(DiagnosisRecord.class);
        verify(repository).save(captor.capture());

        // "SIN REGISTRAR" es la verdad; adivinar una zona falsearia el mapa.
        assertThat(captor.getValue().zona())
                .isEqualTo(DiagnosisRecord.ZONA_SIN_REGISTRAR);
    }

    @Test
    @DisplayName("Una edad desconocida tampoco se inventa")
    void edadDesconocidaNoSeInventa() {
        when(repository.findById("9")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.registrar(evento(9L, "J11", "Comas", null, false));

        ArgumentCaptor<DiagnosisRecord> captor =
                ArgumentCaptor.forClass(DiagnosisRecord.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().grupoEdad())
                .isEqualTo(DiagnosisRecord.EDAD_SIN_REGISTRAR);
        assertThat(captor.getValue().edad()).isNull();
    }

    @Test
    @DisplayName("Los grupos de edad separan los tramos que importan")
    void gruposDeEdad() {
        // No son tramos iguales: separan los grupos que de verdad se comportan
        // distinto ante una enfermedad.
        assertThat(DiagnosisRecord.grupoDeEdad(0)).isEqualTo("MENOR DE 1");
        assertThat(DiagnosisRecord.grupoDeEdad(4)).isEqualTo("1 A 4");
        assertThat(DiagnosisRecord.grupoDeEdad(5)).isEqualTo("5 A 11");
        assertThat(DiagnosisRecord.grupoDeEdad(11)).isEqualTo("5 A 11");
        assertThat(DiagnosisRecord.grupoDeEdad(12)).isEqualTo("12 A 17");
        assertThat(DiagnosisRecord.grupoDeEdad(18)).isEqualTo("18 A 29");
        assertThat(DiagnosisRecord.grupoDeEdad(30)).isEqualTo("30 A 59");
        assertThat(DiagnosisRecord.grupoDeEdad(59)).isEqualTo("30 A 59");
        assertThat(DiagnosisRecord.grupoDeEdad(60)).isEqualTo("60 Y MAS");
        assertThat(DiagnosisRecord.grupoDeEdad(95)).isEqualTo("60 Y MAS");
    }

    @Test
    @DisplayName("Una edad negativa se trata como desconocida")
    void edadNegativaEsDesconocida() {
        assertThat(DiagnosisRecord.grupoDeEdad(-1))
                .isEqualTo(DiagnosisRecord.EDAD_SIN_REGISTRAR);
    }

    @Test
    @DisplayName("Marca el diagnostico de notificacion obligatoria")
    void marcaElNotificable() {
        when(repository.findById("10")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.registrar(evento(10L, "A90", "Comas", 25, true));

        ArgumentCaptor<DiagnosisRecord> captor =
                ArgumentCaptor.forClass(DiagnosisRecord.class);
        verify(repository).save(captor.capture());

        // Exige aviso al MINSA con un solo caso, sin esperar ningun umbral.
        assertThat(captor.getValue().notificable()).isTrue();
    }

    @Test
    @DisplayName("El resumen cuenta diagnosticos y notificables por separado")
    void elResumenCuentaPorSeparado() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        when(repository.contarEntre(desde, hasta)).thenReturn(240L);
        when(repository.contarNotificablesEntre(desde, hasta)).thenReturn(3L);

        var resumen = service.resumen(desde, hasta);

        assertThat(resumen.diagnosticos()).isEqualTo(240L);
        assertThat(resumen.notificables()).isEqualTo(3L);
    }

    private DiagnosisEvent evento(Long id, String cie10, String zona,
                                  Integer edad, boolean notificable) {
        return DiagnosisEvent.registrado(
                "req-1", id, 100L, 3L, "71234567", zona, edad, "FEMENINO",
                5L, cie10, "Descripcion de " + cie10, true, notificable,
                LocalDate.of(2026, 10, 31));
    }
}
