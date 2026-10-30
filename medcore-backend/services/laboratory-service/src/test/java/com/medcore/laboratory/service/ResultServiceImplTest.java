package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.document.ResultDocument;
import com.medcore.laboratory.domain.dto.request.ResultEntryRequest;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.entity.TestCatalog;
import com.medcore.laboratory.domain.enums.ResultInterpretation;
import com.medcore.laboratory.event.producer.ResultEventProducer;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import com.medcore.laboratory.mapper.ResultMapper;
import com.medcore.laboratory.repository.ResultRepository;
import com.medcore.laboratory.repository.SampleRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.repository.mongo.ResultDocumentRepository;
import com.medcore.laboratory.service.impl.ResultServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del registro de resultados.
 *
 * <p>Fuera de una transaccion real, {@code publicarTrasCommit} publica de
 * inmediato, porque no hay sincronizacion activa. Eso permite verificar aqui
 * que se publica lo correcto; que se publique DESPUES del commit es lo que
 * garantiza el propio TransactionSynchronizationManager en ejecucion.
 */
@ExtendWith(MockitoExtension.class)
class ResultServiceImplTest {

    private static final Long ID_MUESTRA = 7L;
    private static final String HEMOGLOBINA = "HEM-001";

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private SampleRepository sampleRepository;

    @Mock
    private TestCatalogRepository catalogoRepository;

    @Mock
    private ResultDocumentRepository documentoRepository;

    @Mock
    private ValidationService validationService;

    @Mock
    private CriticalValueService criticalValueService;

    @Mock
    private ResultEventProducer eventProducer;

    private ResultServiceImpl resultService;

    private Sample muestra;

    @BeforeEach
    void prepararServicio() {
        resultService = new ResultServiceImpl(
                resultRepository, sampleRepository, catalogoRepository, documentoRepository,
                validationService, criticalValueService, eventProducer, new ResultMapper());

        muestra = muestraEnProceso();

        // El repositorio devuelve lo que se le pasa, con un id asignado, igual
        // que haria la base al insertar.
        lenient().when(resultRepository.save(any(Result.class))).thenAnswer(invocacion -> {
            Result guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) {
                ReflectionTestUtils.setField(guardado, "id", 100L);
            }
            if (guardado.getFechaResultado() == null) {
                ReflectionTestUtils.setField(guardado, "fechaResultado", Instant.now());
            }
            return guardado;
        });
    }

    @Test
    @DisplayName("Registra un resultado manual con su interpretacion")
    void registraUnResultadoManual() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(HEMOGLOBINA))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
        when(resultRepository.existsByMuestraIdAndCodigoPrueba(ID_MUESTRA, HEMOGLOBINA))
                .thenReturn(false);
        when(validationService.interpretar(HEMOGLOBINA, 9.8, "FEMENINO", 34))
                .thenReturn(new ValidationService.Interpretacion(
                        ResultInterpretation.BAJO, "12.0 - 15.5 g/dL"));

        var respuesta = resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest(HEMOGLOBINA, 9.8, null, null), "tecnico1", "req-1");

        assertThat(respuesta.valorNumerico()).isEqualTo(9.8);
        assertThat(respuesta.interpretacion()).isEqualTo(ResultInterpretation.BAJO);
        assertThat(respuesta.etiquetaInterpretacion()).isEqualTo("Bajo");
        assertThat(respuesta.rangoReferencia()).isEqualTo("12.0 - 15.5 g/dL");
        assertThat(respuesta.critico()).isFalse();
        assertThat(respuesta.origen()).isEqualTo("MANUAL");
        assertThat(respuesta.esCorreccion()).isFalse();

        verify(eventProducer).publicarRegistrado(any(), eq(muestra), eq("req-1"));
        // No es critico: no debe salir el aviso al medico.
        verify(eventProducer, never()).publicarCritico(any(), any(), anyString());
    }

    @Test
    @DisplayName("Un valor critico publica result.critical y deja traza local")
    void unValorCriticoPublicaElAviso() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(HEMOGLOBINA))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
        when(validationService.interpretar(HEMOGLOBINA, 6.2, "FEMENINO", 34))
                .thenReturn(new ValidationService.Interpretacion(
                        ResultInterpretation.CRITICO_BAJO, "12.0 - 15.5 g/dL"));

        var respuesta = resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest(HEMOGLOBINA, 6.2, null, null), "tecnico1", "req-2");

        assertThat(respuesta.critico()).isTrue();

        // Los dos topicos: un valor critico tambien forma parte del historial
        // y de las estadisticas.
        verify(eventProducer).publicarRegistrado(any(), eq(muestra), eq("req-2"));
        verify(eventProducer).publicarCritico(any(), eq(muestra), eq("req-2"));
        // Traza local, por si el aviso de notification-service falla.
        verify(criticalValueService).registrarAviso(any(), eq(muestra), eq("req-2"));
    }

    @Test
    @DisplayName("Rechaza registrar sobre una muestra que no esta en proceso")
    void rechazaRegistrarSobreUnaMuestraQueNoEstaEnProceso() {
        Sample recienRegistrada = muestraRecienRegistrada();
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(recienRegistrada));

        assertThatThrownBy(() -> resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest(HEMOGLOBINA, 13.0, null, null), "tecnico1", "req-3"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("solo se pueden registrar resultados mientras esta EN_PROCESO");

        verify(resultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza un segundo resultado de la misma prueba")
    void rechazaUnResultadoDuplicado() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(HEMOGLOBINA))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
        when(resultRepository.existsByMuestraIdAndCodigoPrueba(ID_MUESTRA, HEMOGLOBINA))
                .thenReturn(true);

        assertThatThrownBy(() -> resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest(HEMOGLOBINA, 13.0, null, null), "tecnico1", "req-4"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("Registre una correccion");
    }

    @Test
    @DisplayName("Una prueba cuantitativa exige valor numerico")
    void unaPruebaCuantitativaExigeValorNumerico() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(HEMOGLOBINA))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));

        // Sin numero no se puede comparar con el rango ni detectar un critico.
        assertThatThrownBy(() -> resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest(HEMOGLOBINA, null, "nueve coma ocho", null),
                "tecnico1", "req-5"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("es cuantitativa y requiere un valor numerico");
    }

    @Test
    @DisplayName("Una prueba cualitativa acepta solo texto")
    void unaPruebaCualitativaAceptaSoloTexto() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById("MIC-001"))
                .thenReturn(Optional.of(prueba("MIC-001", "Urocultivo", null, false)));
        when(validationService.interpretar("MIC-001", null, "FEMENINO", 34))
                .thenReturn(ValidationService.Interpretacion.sinRango());

        var respuesta = resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest("MIC-001", null,
                        "Escherichia coli, mas de 100000 UFC/mL", null),
                "tecnico1", "req-6");

        assertThat(respuesta.valorNumerico()).isNull();
        assertThat(respuesta.valorTexto()).startsWith("Escherichia coli");
        assertThat(respuesta.interpretacion()).isEqualTo(ResultInterpretation.NO_APLICA);
    }

    @Test
    @DisplayName("Rechaza un resultado sin ningun valor")
    void rechazaUnResultadoSinValor() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById("MIC-001"))
                .thenReturn(Optional.of(prueba("MIC-001", "Urocultivo", null, false)));

        assertThatThrownBy(() -> resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest("MIC-001", null, "   ", null), "tecnico1", "req-7"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("Indique un valor numerico o un valor de texto");
    }

    @Test
    @DisplayName("Rechaza una prueba dada de baja del catalogo")
    void rechazaUnaPruebaInactiva() {
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        TestCatalog inactiva = prueba("HEM-099", "Prueba retirada", "g/dL", true);
        ReflectionTestUtils.setField(inactiva, "activa", false);
        when(catalogoRepository.findById("HEM-099")).thenReturn(Optional.of(inactiva));

        assertThatThrownBy(() -> resultService.registrarManual(ID_MUESTRA,
                new ResultEntryRequest("HEM-099", 1.0, null, null), "tecnico1", "req-8"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("esta dada de baja en el catalogo");
    }

    @Test
    @DisplayName("Registra los resultados de una trama y guarda la trama cruda")
    void registraLosResultadosDeUnaTrama() {
        when(sampleRepository.findByCodigo("MUE-20261029-0012")).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(HEMOGLOBINA))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
        when(catalogoRepository.findById("HEM-004"))
                .thenReturn(Optional.of(prueba("HEM-004", "Plaquetas", "x10^3/uL", true)));
        // El analizador envia tambien un parametro interno que el laboratorio
        // no informa: no esta en el catalogo.
        when(catalogoRepository.findById("XN-RBC")).thenReturn(Optional.empty());
        when(documentoRepository.findByIdMuestraAndIdMensaje(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(documentoRepository.save(any(ResultDocument.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(validationService.interpretarConFlag(anyString(), any(), any(), any(), any()))
                .thenReturn(new ValidationService.Interpretacion(
                        ResultInterpretation.BAJO, "12.0 - 15.5 g/dL"));

        var resultados = resultService.registrarDesdeEquipo(
                tramaParseada(), "MSH|^~\\&|SYSMEX_XN550|...", "req-9");

        // Dos de las tres observaciones: la del parametro interno se omite.
        assertThat(resultados).hasSize(2);
        assertThat(resultados).allMatch(r -> "EQUIPO".equals(r.origen()));

        ArgumentCaptor<ResultDocument> documento = ArgumentCaptor.forClass(ResultDocument.class);
        verify(documentoRepository).save(documento.capture());
        // La trama literal se guarda sin tocar, para poder reprocesarla si se
        // descubre que el parser leia mal un campo.
        assertThat(documento.getValue().tramaOriginal()).startsWith("MSH|^~\\&|SYSMEX_XN550");
        assertThat(documento.getValue().formato()).isEqualTo("HL7");
        assertThat(documento.getValue().equipo()).isEqualTo("SYSMEX_XN550");

        verify(eventProducer, times(2)).publicarRegistrado(any(), eq(muestra), eq("req-9"));
    }

    @Test
    @DisplayName("Reutiliza el documento crudo cuando el equipo reenvia el mismo mensaje")
    void reutilizaElDocumentoDeUnReenvio() {
        // Caso detectado contra el stack real: el indice unico de Mongo por
        // muestra y control id hacia fallar el segundo insert con
        // DuplicateKeyException, y el equipo recibia un ACK de error por un
        // mensaje que ya estaba guardado.
        when(sampleRepository.findByCodigo("MUE-20261029-0012")).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(anyString()))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));

        ResultDocument yaGuardado = ResultDocument.deEquipo(
                ID_MUESTRA, "MUE-20261029-0012", 10L, 3L, "SYSMEX_XN550",
                "MSG00042", "MSH|...", "HL7", Map.of());
        when(documentoRepository.findByIdMuestraAndIdMensaje(
                String.valueOf(ID_MUESTRA), "MSG00042"))
                .thenReturn(Optional.of(yaGuardado));
        // Los resultados tambien estan ya registrados del primer envio.
        when(resultRepository.existsByMuestraIdAndCodigoPrueba(anyLong(), anyString()))
                .thenReturn(true);

        var resultados = resultService.registrarDesdeEquipo(
                tramaParseada(), "MSH|...", "req-10");

        assertThat(resultados).isEmpty();
        // No se inserta un segundo documento ni se repiten los resultados.
        verify(documentoRepository, never()).save(any());
        verify(resultRepository, never()).save(any());
        verify(eventProducer, never()).publicarRegistrado(any(), any(), anyString());
    }

    @Test
    @DisplayName("Una tanda nueva de la misma muestra si crea otro documento")
    void unaTandaNuevaCreaOtroDocumento() {
        // Un equipo puede enviar un hemograma y luego la formula diferencial
        // de la misma muestra: otro control id, otro documento.
        when(sampleRepository.findByCodigo("MUE-20261029-0012")).thenReturn(Optional.of(muestra));
        when(catalogoRepository.findById(anyString()))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
        when(documentoRepository.findByIdMuestraAndIdMensaje(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(documentoRepository.save(any(ResultDocument.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(validationService.interpretarConFlag(anyString(), any(), any(), any(), any()))
                .thenReturn(new ValidationService.Interpretacion(
                        ResultInterpretation.NORMAL, "12.0 - 15.5 g/dL"));

        var resultados = resultService.registrarDesdeEquipo(
                tramaParseada(), "MSH|...", "req-13");

        assertThat(resultados).isNotEmpty();
        verify(documentoRepository).save(any(ResultDocument.class));
    }

    @Test
    @DisplayName("Una trama ASTM, sin control id, se identifica por su huella")
    void unaTramaAstmSeIdentificaPorSuHuella() {
        // ASTM no tiene campo equivalente a MSH-10, asi que el identificador
        // sale de la propia trama: identico en un reenvio, distinto en una
        // tanda nueva.
        var sinControlId = ResultDocument.deEquipo(
                ID_MUESTRA, "MUE-20261029-0013", 10L, 3L, "ROCHE_COBAS_C311",
                null, "H|||ROCHE_COBAS_C311", "ASTM", Map.of());
        var mismaTrama = ResultDocument.deEquipo(
                ID_MUESTRA, "MUE-20261029-0013", 10L, 3L, "ROCHE_COBAS_C311",
                null, "H|||ROCHE_COBAS_C311", "ASTM", Map.of());
        var otraTrama = ResultDocument.deEquipo(
                ID_MUESTRA, "MUE-20261029-0013", 10L, 3L, "ROCHE_COBAS_C311",
                null, "H|||ROCHE_COBAS_C311|OTRO", "ASTM", Map.of());

        assertThat(sinControlId.idMensaje()).startsWith("HUELLA-");
        assertThat(mismaTrama.idMensaje()).isEqualTo(sinControlId.idMensaje());
        assertThat(otraTrama.idMensaje()).isNotEqualTo(sinControlId.idMensaje());
    }

    @Test
    @DisplayName("Rechaza una trama cuya muestra no existe")
    void rechazaUnaTramaDeMuestraInexistente() {
        when(sampleRepository.findByCodigo("MUE-20261029-0012")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resultService.registrarDesdeEquipo(
                tramaParseada(), "MSH|...", "req-11"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("No existe ninguna muestra con el codigo");
    }

    @Test
    @DisplayName("La correccion anade un resultado nuevo y apunta al anterior")
    void laCorreccionApuntaAlResultadoAnterior() {
        Result anterior = new Result(muestra, HEMOGLOBINA, "Hemoglobina",
                9.8, null, "g/dL", "MANUAL", "tecnico1");
        ReflectionTestUtils.setField(anterior, "id", 55L);

        when(resultRepository.findById(55L)).thenReturn(Optional.of(anterior));
        when(catalogoRepository.findById(HEMOGLOBINA))
                .thenReturn(Optional.of(prueba(HEMOGLOBINA, "Hemoglobina", "g/dL", true)));
        when(validationService.interpretar(HEMOGLOBINA, 13.8, "FEMENINO", 34))
                .thenReturn(new ValidationService.Interpretacion(
                        ResultInterpretation.NORMAL, "12.0 - 15.5 g/dL"));

        var respuesta = resultService.corregir(55L,
                new ResultEntryRequest(HEMOGLOBINA, 13.8, null, "Error de transcripcion"),
                "supervisor", "req-12");

        assertThat(respuesta.esCorreccion()).isTrue();
        assertThat(respuesta.idResultadoCorregido()).isEqualTo(55L);
        assertThat(respuesta.observaciones()).isEqualTo("Error de transcripcion");
        assertThat(respuesta.valorNumerico()).isEqualTo(13.8);

        // El anterior no se modifica: un resultado informado es un registro
        // clinico y el historial conserva lo que se dijo en su momento.
        assertThat(anterior.getValorNumerico()).isEqualTo(9.8);
        assertThat(anterior.getIdResultadoCorregido()).isNull();
    }

    // ---- Datos de prueba ---------------------------------------------------

    private Hl7ParsedResult tramaParseada() {
        return new Hl7ParsedResult(
                "MUE-20261029-0012",
                "SYSMEX_XN550",
                "MSG00042",
                "HL7",
                Instant.now(),
                List.of(
                        new Hl7ParsedResult.Observacion(
                                HEMOGLOBINA, "Hemoglobina", 9.8, "9.8", "g/dL", "L"),
                        new Hl7ParsedResult.Observacion(
                                "HEM-004", "Plaquetas", 42.0, "42", "x10^3/uL", "LL"),
                        new Hl7ParsedResult.Observacion(
                                "XN-RBC", "Eritrocitos internos", 3.21, "3.21", "x10^6/uL", "N")),
                Map.of("MSH-3", "SYSMEX_XN550"));
    }

    private Sample muestraEnProceso() {
        Sample sample = muestraRecienRegistrada();
        sample.iniciarProceso();
        return sample;
    }

    private Sample muestraRecienRegistrada() {
        Sample sample = new Sample(
                "MUE-20261029-0012", 10L, "ORD-20261029-0001", 3L,
                "44556677", "PEREZ QUISPE, Maria Elena", "maria@correo.local",
                "FEMENINO", 34, "San Juan de Lurigancho",
                9L, "SANGRE", false, "tecnico1");
        ReflectionTestUtils.setField(sample, "id", ID_MUESTRA);
        ReflectionTestUtils.setField(sample, "fechaToma", Instant.now());
        return sample;
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

    /**
     * TestCatalog solo existe como fila cargada por las migraciones: la
     * aplicacion nunca la construye, asi que su constructor queda protegido
     * para JPA. Se instancia por reflexion en lugar de abrirlo, para no
     * anadir a la entidad una puerta que solo usarian las pruebas.
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
}
