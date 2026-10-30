package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.entity.CriticalValue;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.enums.ResultInterpretation;
import com.medcore.laboratory.mapper.ResultMapper;
import com.medcore.laboratory.repository.CriticalValueRepository;
import com.medcore.laboratory.repository.ResultRepository;
import com.medcore.laboratory.service.impl.CriticalValueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriticalValueServiceImplTest {

    private static final String HEMOGLOBINA = "HEM-001";
    private static final String PLAQUETAS = "HEM-004";

    @Mock
    private CriticalValueRepository criticoRepository;

    @Mock
    private ResultRepository resultRepository;

    private CriticalValueServiceImpl criticalValueService;

    @BeforeEach
    void prepararServicio() {
        criticalValueService = new CriticalValueServiceImpl(
                criticoRepository, resultRepository, new ResultMapper());
    }

    @Test
    @DisplayName("Devuelve los limites configurados de una prueba")
    void devuelveLosLimitesDeUnaPrueba() {
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(critico(HEMOGLOBINA, 7.0, 20.0,
                        "Anemia severa o policitemia")));

        var limites = criticalValueService.limitesDe(HEMOGLOBINA);

        assertThat(limites).isPresent();
        assertThat(limites.get().limiteInferior()).isEqualTo(7.0);
        assertThat(limites.get().limiteSuperior()).isEqualTo(20.0);
        assertThat(limites.get().indicacion()).contains("Anemia severa");
    }

    @Test
    @DisplayName("Una prueba critica solo por abajo deja vacio el limite superior")
    void unaPruebaCriticaSoloPorAbajoNoTieneLimiteSuperior() {
        // Las plaquetas solo son criticas cuando estan muy bajas.
        when(criticoRepository.findByCodigoPrueba(PLAQUETAS))
                .thenReturn(Optional.of(critico(PLAQUETAS, 50.0, null,
                        "Riesgo de sangrado espontaneo")));

        var limites = criticalValueService.limitesDe(PLAQUETAS).orElseThrow();

        assertThat(limites.limiteInferior()).isEqualTo(50.0);
        assertThat(limites.limiteSuperior()).isNull();
    }

    @Test
    @DisplayName("Una prueba sin limites configurados devuelve vacio")
    void unaPruebaSinLimitesDevuelveVacio() {
        when(criticoRepository.findByCodigoPrueba("MIC-001")).thenReturn(Optional.empty());

        assertThat(criticalValueService.limitesDe("MIC-001")).isEmpty();
    }

    @Test
    @DisplayName("Registra el aviso aunque la prueba no tenga indicacion configurada")
    void registraElAvisoSinIndicacionConfigurada() {
        // El aviso no debe depender de que el catalogo tenga texto: si falta,
        // se usa uno por omision. Perder la traza de un valor critico por un
        // campo vacio seria lo peor que podria pasar aqui.
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(Optional.empty());

        Sample muestra = muestra();
        Result resultado = resultadoCritico(muestra);

        assertThatCode(() -> criticalValueService.registrarAviso(resultado, muestra, "req-1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Registra el aviso de un resultado cualitativo, sin valor numerico")
    void registraElAvisoDeUnResultadoCualitativo() {
        when(criticoRepository.findByCodigoPrueba("MIC-002"))
                .thenReturn(Optional.of(critico("MIC-002", 1.0, null, "Aislar al paciente")));

        Sample muestra = muestra();
        Result resultado = new Result(muestra, "MIC-002", "Coprocultivo",
                null, "Vibrio cholerae", null, "MANUAL", "tecnico1");
        resultado.interpretar(ResultInterpretation.CRITICO_ALTO, null);

        // El log imprime el valor de texto cuando no hay numerico; sin ese
        // respaldo saldria un "null" en la traza del valor critico.
        assertThatCode(() -> criticalValueService.registrarAviso(resultado, muestra, "req-2"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Lista los valores criticos del dia")
    void listaLosValoresCriticosDelDia() {
        Sample muestra = muestra();
        when(resultRepository.criticosEntre(any(), any()))
                .thenReturn(List.of(resultadoCritico(muestra)));

        var criticos = criticalValueService.criticosDeHoy();

        assertThat(criticos).hasSize(1);
        assertThat(criticos.get(0).critico()).isTrue();
        assertThat(criticos.get(0).etiquetaInterpretacion()).isEqualTo("Critico bajo");
        assertThat(criticos.get(0).codigoMuestra()).isEqualTo("MUE-20261029-0012");
    }

    @Test
    @DisplayName("Cuenta el total historico de valores criticos")
    void cuentaElTotalHistorico() {
        when(resultRepository.countByCriticoTrue()).thenReturn(17L);

        assertThat(criticalValueService.totalHistorico()).isEqualTo(17L);
    }

    // ---- Datos de prueba ---------------------------------------------------

    private Result resultadoCritico(Sample muestra) {
        Result resultado = new Result(muestra, HEMOGLOBINA, "Hemoglobina",
                6.2, null, "g/dL", "MANUAL", "tecnico1");
        resultado.interpretar(ResultInterpretation.CRITICO_BAJO, "12.0 - 15.5 g/dL");
        ReflectionTestUtils.setField(resultado, "id", 100L);
        ReflectionTestUtils.setField(resultado, "fechaResultado", Instant.now());
        return resultado;
    }

    private Sample muestra() {
        Sample sample = new Sample(
                "MUE-20261029-0012", 10L, "ORD-20261029-0001", 3L,
                "44556677", "PEREZ QUISPE, Maria Elena", "maria@correo.local",
                "FEMENINO", 34, "San Juan de Lurigancho",
                9L, "SANGRE", false, "tecnico1");
        ReflectionTestUtils.setField(sample, "id", 7L);
        ReflectionTestUtils.setField(sample, "fechaToma", Instant.now());
        return sample;
    }

    /**
     * CriticalValue solo existe como fila cargada por las migraciones: la
     * aplicacion nunca la construye, asi que su constructor queda protegido
     * para JPA y aqui se instancia por reflexion.
     */
    private CriticalValue critico(String codigoPrueba, Double inferior,
                                  Double superior, String indicacion) {
        try {
            var constructor = CriticalValue.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            CriticalValue critico = constructor.newInstance();
            ReflectionTestUtils.setField(critico, "codigoPrueba", codigoPrueba);
            ReflectionTestUtils.setField(critico, "limiteInferior", inferior);
            ReflectionTestUtils.setField(critico, "limiteSuperior", superior);
            ReflectionTestUtils.setField(critico, "indicacion", indicacion);
            return critico;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo instanciar CriticalValue", e);
        }
    }
}
