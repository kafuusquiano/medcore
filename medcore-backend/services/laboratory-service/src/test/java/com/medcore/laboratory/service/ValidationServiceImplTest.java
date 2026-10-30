package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.entity.CriticalValue;
import com.medcore.laboratory.domain.entity.ReferenceRange;
import com.medcore.laboratory.domain.entity.TestCatalog;
import com.medcore.laboratory.domain.enums.ResultInterpretation;
import com.medcore.laboratory.repository.CriticalValueRepository;
import com.medcore.laboratory.repository.ReferenceRangeRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.service.impl.ValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la interpretacion de valores, que es el nucleo clinico del
 * servicio: decide si un numero es normal, anormal o critico.
 *
 * <p>Los datos imitan al catalogo real sembrado en V1: hemoglobina con rangos
 * distintos por sexo y limites criticos en 7.0 y 20.0 g/dL.
 */
@ExtendWith(MockitoExtension.class)
class ValidationServiceImplTest {

    private static final String HEMOGLOBINA = "HEM-001";
    private static final String PLAQUETAS = "HEM-004";
    private static final String UROCULTIVO = "MIC-001";

    @Mock
    private ReferenceRangeRepository rangoRepository;

    @Mock
    private CriticalValueRepository criticoRepository;

    @Mock
    private TestCatalogRepository catalogoRepository;

    @InjectMocks
    private ValidationServiceImpl validationService;

    @BeforeEach
    void prepararCatalogo() {
        // lenient porque no todas las pruebas llegan a consultar el catalogo:
        // las que no encuentran rango aplicable salen antes.
        lenient().when(catalogoRepository.findById(anyString()))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
    }

    @Test
    @DisplayName("Un valor dentro del rango del sexo es normal")
    void unValorDentroDelRangoEsNormal() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        var resultado = validationService.interpretar(HEMOGLOBINA, 15.0, "MASCULINO", 30);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.NORMAL);
        assertThat(resultado.rangoReferencia()).isEqualTo("13.5 - 17.5 g/dL");
        assertThat(resultado.esCritica()).isFalse();
    }

    @Test
    @DisplayName("El mismo valor es normal en un varon y bajo en una mujer")
    void elRangoDependeDelSexo() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        // 13.0 g/dL: por debajo del minimo del varon (13.5), dentro del de la
        // mujer (12.0 a 15.5). Es el caso que justifica guardar un rango por
        // sexo en lugar de uno solo.
        var enVaron = validationService.interpretar(HEMOGLOBINA, 13.0, "MASCULINO", 30);
        var enMujer = validationService.interpretar(HEMOGLOBINA, 13.0, "FEMENINO", 30);

        assertThat(enVaron.interpretacion()).isEqualTo(ResultInterpretation.BAJO);
        assertThat(enMujer.interpretacion()).isEqualTo(ResultInterpretation.NORMAL);
    }

    @Test
    @DisplayName("Un valor por debajo del limite critico se marca CRITICO_BAJO")
    void unValorBajoElLimiteCriticoEsCritico() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        var resultado = validationService.interpretar(HEMOGLOBINA, 6.2, "FEMENINO", 40);

        // Critico, no solo BAJO: 6.2 esta tambien fuera del rango, y lo que
        // debe informarse es lo mas grave.
        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.CRITICO_BAJO);
        assertThat(resultado.esCritica()).isTrue();
        // El rango se conserva aunque el valor sea critico: el informe debe
        // mostrar contra que se comparo.
        assertThat(resultado.rangoReferencia()).isEqualTo("12.0 - 15.5 g/dL");
    }

    @Test
    @DisplayName("Una prueba critica solo por abajo no marca critico un valor alto")
    void unaPruebaCriticaSoloPorAbajoNoMarcaCriticoUnValorAlto() {
        // Las plaquetas solo son criticas cuando estan muy bajas: un recuento
        // alto es anormal pero no una urgencia.
        when(rangoRepository.findByCodigoPrueba(PLAQUETAS)).thenReturn(List.of(
                rango(PLAQUETAS, null, null, null, 150.0, 450.0)));
        when(criticoRepository.findByCodigoPrueba(PLAQUETAS)).thenReturn(Optional.of(
                critico(PLAQUETAS, 50.0, null)));

        var alto = validationService.interpretar(PLAQUETAS, 700.0, "MASCULINO", 30);
        var bajo = validationService.interpretar(PLAQUETAS, 30.0, "MASCULINO", 30);

        assertThat(alto.interpretacion()).isEqualTo(ResultInterpretation.ALTO);
        assertThat(alto.esCritica()).isFalse();
        assertThat(bajo.interpretacion()).isEqualTo(ResultInterpretation.CRITICO_BAJO);
    }

    @Test
    @DisplayName("Sin sexo conocido se descarta un rango que lo exige")
    void sinSexoConocidoSeDescartaElRangoPorSexo() {
        // Ocurre cuando patient-service no responde al registrar la muestra.
        // Aplicar el rango del varon a quien no se sabe si lo es daria una
        // interpretacion con apariencia de precisa y sin fundamento.
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        // La edad si se conoce, asi que el rango pediatrico de 0 a 14 anios
        // es aplicable y es el unico que no exige sexo.
        var resultado = validationService.interpretar(HEMOGLOBINA, 13.0, null, 10);

        assertThat(resultado.rangoReferencia()).isEqualTo("11.0 - 14.0 g/dL");
        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.NORMAL);
    }

    @Test
    @DisplayName("Sin sexo ni edad no se aplica ningun rango acotado")
    void sinSexoNiEdadNoSeAplicaNingunRangoAcotado() {
        // Caso detectado contra el stack real: una muestra de una mujer de 34
        // anios, registrada mientras patient-service estaba caido, se
        // interpreto contra el rango pediatrico de 11.0 a 14.0 g/dL porque era
        // el unico sin sexo. El informe mostraba un rango que no era el suyo,
        // que es peor que no mostrar ninguno.
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        var resultado = validationService.interpretar(HEMOGLOBINA, 9.8, null, null);

        // Ningun rango aplica: todos acotan sexo o edad. El valor se informa
        // sin interpretar.
        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.NO_APLICA);
        assertThat(resultado.rangoReferencia()).isNull();
    }

    @Test
    @DisplayName("Los limites criticos siguen aplicando sin sexo ni edad")
    void losLimitesCriticosAplicanSinSexoNiEdad() {
        // Los limites criticos no dependen del sexo ni de la edad en el
        // catalogo, asi que un valor peligroso se detecta igual aunque no se
        // sepa nada del paciente. Es lo que no debe perderse al endurecer la
        // seleccion de rangos.
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        var resultado = validationService.interpretar(HEMOGLOBINA, 6.2, null, null);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.CRITICO_BAJO);
        assertThat(resultado.esCritica()).isTrue();
        // Sin rango aplicable no hay etiqueta de rango que mostrar.
        assertThat(resultado.rangoReferencia()).isNull();
    }

    @Test
    @DisplayName("Entre dos rangos aplicables gana el de tramo de edad mas estrecho")
    void ganaElRangoDeTramoMasEstrecho() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(List.of(
                rango(HEMOGLOBINA, null, null, null, 11.0, 18.0),
                rango(HEMOGLOBINA, null, 0, 14, 11.0, 14.0)));
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(Optional.empty());

        // Un nino de 10 anios encaja en los dos. El acotado es el que el
        // laboratorio definio pensando en el.
        var resultado = validationService.interpretar(HEMOGLOBINA, 15.0, null, 10);

        assertThat(resultado.rangoReferencia()).isEqualTo("11.0 - 14.0 g/dL");
        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.ALTO);
    }

    @Test
    @DisplayName("Una prueba sin rango definido queda NO_APLICA")
    void unaPruebaSinRangoQuedaNoAplica() {
        when(rangoRepository.findByCodigoPrueba(UROCULTIVO)).thenReturn(List.of());
        when(criticoRepository.findByCodigoPrueba(UROCULTIVO)).thenReturn(Optional.empty());

        var resultado = validationService.interpretar(UROCULTIVO, 1.0, "MASCULINO", 30);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.NO_APLICA);
        assertThat(resultado.rangoReferencia()).isNull();
    }

    @Test
    @DisplayName("Un valor nulo, propio de una prueba cualitativa, queda NO_APLICA")
    void unValorNuloQuedaNoAplica() {
        var resultado = validationService.interpretar(UROCULTIVO, null, "MASCULINO", 30);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.NO_APLICA);
        assertThat(resultado.rangoReferencia()).isNull();
    }

    @Test
    @DisplayName("Los limites propios mandan sobre el flag del equipo")
    void losLimitesPropiosMandanSobreElFlagDelEquipo() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        // El equipo lo marca solo como bajo, pero para el laboratorio 6.2 esta
        // por debajo de su limite critico. El flag del analizador viene
        // calculado con los rangos que tiene de fabrica, que no son los de
        // este laboratorio.
        var resultado = validationService.interpretarConFlag(
                HEMOGLOBINA, 6.2, "L", "FEMENINO", 40);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.CRITICO_BAJO);
    }

    @Test
    @DisplayName("Se respeta el aviso critico del equipo cuando los limites propios no lo detectan")
    void seRespetaElAvisoCriticoDelEquipo() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        // 8.5 esta sobre el limite critico propio (7.0) pero el equipo lo
        // marca HH. Dejar pasar un valor peligroso es peor que avisar de mas.
        var resultado = validationService.interpretarConFlag(
                HEMOGLOBINA, 8.5, "HH", "FEMENINO", 40);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.CRITICO_ALTO);
        assertThat(resultado.esCritica()).isTrue();
    }

    @Test
    @DisplayName("Sin rango propio se usa el flag del equipo")
    void sinRangoPropioSeUsaElFlagDelEquipo() {
        when(rangoRepository.findByCodigoPrueba("INM-900")).thenReturn(List.of());
        when(criticoRepository.findByCodigoPrueba("INM-900")).thenReturn(Optional.empty());

        var resultado = validationService.interpretarConFlag(
                "INM-900", 42.0, "H", "MASCULINO", 30);

        // Es mejor que nada: sin rango propio, el servicio no tiene con que
        // comparar y el equipo si midio contra el suyo.
        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.ALTO);
    }

    @Test
    @DisplayName("El flag del equipo no degrada una interpretacion propia")
    void elFlagDelEquipoNoDegradaLaInterpretacionPropia() {
        when(rangoRepository.findByCodigoPrueba(HEMOGLOBINA)).thenReturn(rangosDeHemoglobina());
        when(criticoRepository.findByCodigoPrueba(HEMOGLOBINA))
                .thenReturn(Optional.of(criticoDeHemoglobina()));

        // El equipo dice que 11.0 es normal; con el rango del laboratorio es
        // bajo para una mujer adulta. Manda el del laboratorio.
        var resultado = validationService.interpretarConFlag(
                HEMOGLOBINA, 11.0, "N", "FEMENINO", 40);

        assertThat(resultado.interpretacion()).isEqualTo(ResultInterpretation.BAJO);
    }

    // ---- Datos de prueba ---------------------------------------------------

    private List<ReferenceRange> rangosDeHemoglobina() {
        return List.of(
                rango(HEMOGLOBINA, "MASCULINO", 15, null, 13.5, 17.5),
                rango(HEMOGLOBINA, "FEMENINO", 15, null, 12.0, 15.5),
                rango(HEMOGLOBINA, null, 0, 14, 11.0, 14.0));
    }

    private CriticalValue criticoDeHemoglobina() {
        return critico(HEMOGLOBINA, 7.0, 20.0);
    }

    /**
     * Instancia una entidad cuyo constructor sin argumentos es protegido.
     *
     * <p>ReferenceRange, CriticalValue y TestCatalog solo existen como filas
     * cargadas por las migraciones: la aplicacion nunca las construye, asi
     * que su constructor queda protegido para JPA. Se instancian por
     * reflexion en lugar de abrirlo, para no anadir a la entidad una puerta
     * que solo usarian las pruebas.
     */
    private <T> T instanciar(Class<T> tipo) {
        try {
            var constructor = tipo.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo instanciar " + tipo.getSimpleName(), e);
        }
    }

    private ReferenceRange rango(String codigoPrueba, String sexo, Integer edadMin,
                                 Integer edadMax, Double minimo, Double maximo) {
        ReferenceRange rango = instanciar(ReferenceRange.class);
        ReflectionTestUtils.setField(rango, "codigoPrueba", codigoPrueba);
        ReflectionTestUtils.setField(rango, "sexo", sexo);
        ReflectionTestUtils.setField(rango, "edadMinAnios", edadMin);
        ReflectionTestUtils.setField(rango, "edadMaxAnios", edadMax);
        ReflectionTestUtils.setField(rango, "valorMin", minimo);
        ReflectionTestUtils.setField(rango, "valorMax", maximo);
        return rango;
    }

    private CriticalValue critico(String codigoPrueba, Double inferior, Double superior) {
        CriticalValue critico = instanciar(CriticalValue.class);
        ReflectionTestUtils.setField(critico, "codigoPrueba", codigoPrueba);
        ReflectionTestUtils.setField(critico, "limiteInferior", inferior);
        ReflectionTestUtils.setField(critico, "limiteSuperior", superior);
        ReflectionTestUtils.setField(critico, "indicacion", "Avisar de inmediato");
        return critico;
    }

    private TestCatalog prueba(String codigo, String nombre, String unidad, boolean cuantitativa) {
        TestCatalog prueba = instanciar(TestCatalog.class);
        ReflectionTestUtils.setField(prueba, "codigo", codigo);
        ReflectionTestUtils.setField(prueba, "nombre", nombre);
        ReflectionTestUtils.setField(prueba, "unidad", unidad);
        ReflectionTestUtils.setField(prueba, "cuantitativa", cuantitativa);
        ReflectionTestUtils.setField(prueba, "activa", true);
        return prueba;
    }
}
