package com.medcore.laboratory.service.impl;

import com.medcore.laboratory.domain.document.ResultDocument;
import com.medcore.laboratory.domain.dto.request.ResultEntryRequest;
import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.entity.TestCatalog;
import com.medcore.laboratory.event.producer.ResultEventProducer;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import com.medcore.laboratory.mapper.ResultMapper;
import com.medcore.laboratory.repository.mongo.ResultDocumentRepository;
import com.medcore.laboratory.repository.ResultRepository;
import com.medcore.laboratory.repository.SampleRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.service.CriticalValueService;
import com.medcore.laboratory.service.ResultService;
import com.medcore.laboratory.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ResultServiceImpl implements ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultServiceImpl.class);

    private static final String ORIGEN_MANUAL = "MANUAL";
    private static final String ORIGEN_EQUIPO = "EQUIPO";

    private final ResultRepository resultRepository;
    private final SampleRepository sampleRepository;
    private final TestCatalogRepository catalogoRepository;
    private final ResultDocumentRepository documentoRepository;
    private final ValidationService validationService;
    private final CriticalValueService criticalValueService;
    private final ResultEventProducer eventProducer;
    private final ResultMapper mapper;

    public ResultServiceImpl(ResultRepository resultRepository,
                             SampleRepository sampleRepository,
                             TestCatalogRepository catalogoRepository,
                             ResultDocumentRepository documentoRepository,
                             ValidationService validationService,
                             CriticalValueService criticalValueService,
                             ResultEventProducer eventProducer,
                             ResultMapper mapper) {
        this.resultRepository = resultRepository;
        this.sampleRepository = sampleRepository;
        this.catalogoRepository = catalogoRepository;
        this.documentoRepository = documentoRepository;
        this.validationService = validationService;
        this.criticalValueService = criticalValueService;
        this.eventProducer = eventProducer;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ResultResponse registrarManual(Long idMuestra, ResultEntryRequest peticion,
                                          String usuario, String requestId) {
        Sample muestra = buscarMuestra(idMuestra);
        exigirQueAdmitaResultados(muestra);

        TestCatalog prueba = buscarPruebaActiva(peticion.codigoPrueba());
        validarValores(peticion, prueba);

        if (resultRepository.existsByMuestraIdAndCodigoPrueba(idMuestra, prueba.getCodigo())) {
            throw LaboratoryException.resultadoDuplicado(prueba.getCodigo(), muestra.getCodigo());
        }

        Result resultado = construir(muestra, prueba, peticion.valorNumerico(),
                peticion.valorTexto(), ORIGEN_MANUAL, usuario, null);

        if (peticion.observaciones() != null) {
            resultado.anotar(peticion.observaciones());
        }

        Result guardado = resultRepository.save(resultado);

        log.info("Resultado manual de {} en la muestra {}: {} [{}]",
                prueba.getCodigo(), muestra.getCodigo(),
                guardado.getInterpretacion(), requestId);

        publicarTrasCommit(guardado, muestra, requestId);

        return mapper.aRespuesta(guardado);
    }

    @Override
    @Transactional
    public List<ResultResponse> registrarDesdeEquipo(Hl7ParsedResult parseado,
                                                     String tramaOriginal, String requestId) {
        Sample muestra = sampleRepository.findByCodigo(parseado.codigoMuestra())
                .orElseThrow(() -> LaboratoryException.muestraNoEncontradaPorCodigo(
                        parseado.codigoMuestra()));

        exigirQueAdmitaResultados(muestra);

        // La trama cruda se guarda antes de interpretarla. Si el mapeo falla
        // en la observacion tercera de cinco, lo que llego sigue estando
        // completo en Mongo y se puede reprocesar.
        ResultDocument documento = guardarTramaCruda(muestra, parseado, tramaOriginal);

        List<Result> guardados = new ArrayList<>();

        for (Hl7ParsedResult.Observacion observacion : parseado.observaciones()) {
            Optional<TestCatalog> enCatalogo = catalogoRepository.findById(observacion.codigoPrueba());

            // Un analizador envia parametros internos que el laboratorio no
            // informa. Se omiten en lugar de rechazar la trama entera.
            if (enCatalogo.isEmpty()) {
                log.debug("El equipo {} envio la prueba {}, que no esta en el catalogo; se omite",
                        parseado.equipo(), observacion.codigoPrueba());
                continue;
            }

            TestCatalog prueba = enCatalogo.get();

            // Un reenvio del equipo no debe duplicar el resultado. Los
            // analizadores reenvian cuando no reciben el ACK a tiempo.
            if (resultRepository.existsByMuestraIdAndCodigoPrueba(
                    muestra.getId(), prueba.getCodigo())) {
                log.debug("La prueba {} ya tiene resultado en la muestra {}; se omite el reenvio",
                        prueba.getCodigo(), muestra.getCodigo());
                continue;
            }

            Result resultado = construirDesdeEquipo(muestra, prueba, observacion,
                    parseado.equipo(), documento.id());

            guardados.add(resultRepository.save(resultado));
        }

        log.info("Trama {} del equipo {}: {} de {} observaciones registradas en la muestra {} [{}]",
                parseado.formato(), parseado.equipo(), guardados.size(),
                parseado.observaciones().size(), muestra.getCodigo(), requestId);

        guardados.forEach(resultado -> publicarTrasCommit(resultado, muestra, requestId));

        return guardados.stream().map(mapper::aRespuesta).toList();
    }

    @Override
    @Transactional
    public ResultResponse corregir(Long idResultado, ResultEntryRequest peticion,
                                   String usuario, String requestId) {
        Result anterior = resultRepository.findById(idResultado)
                .orElseThrow(() -> LaboratoryException.resultadoNoEncontrado(idResultado));

        Sample muestra = anterior.getMuestra();
        TestCatalog prueba = buscarPruebaActiva(anterior.getCodigoPrueba());
        validarValores(peticion, prueba);

        // La correccion se registra aunque la muestra ya este cerrada: un
        // error detectado despues de informar es precisamente el caso que
        // obliga a corregir.
        Result correccion = construir(muestra, prueba, peticion.valorNumerico(),
                peticion.valorTexto(), ORIGEN_MANUAL, usuario, null);

        correccion.marcarComoCorreccionDe(anterior.getId());
        correccion.anotar(peticion.observaciones() == null
                ? "Corrige el resultado " + anterior.getId()
                : peticion.observaciones());

        Result guardado = resultRepository.save(correccion);

        log.info("Resultado {} corregido por {} en la muestra {} [{}]",
                anterior.getId(), guardado.getId(), muestra.getCodigo(), requestId);

        publicarTrasCommit(guardado, muestra, requestId);

        return mapper.aRespuesta(guardado);
    }

    @Override
    public List<ResultResponse> listarDeMuestra(Long idMuestra) {
        return resultRepository.findByMuestraIdOrderByCodigoPrueba(idMuestra).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    public ResultResponse obtenerPorId(Long id) {
        return resultRepository.findById(id)
                .map(mapper::aRespuesta)
                .orElseThrow(() -> LaboratoryException.resultadoNoEncontrado(id));
    }

    @Override
    public List<ResultResponse> criticosDeHoy() {
        LocalDate hoy = LocalDate.now();
        Instant desde = hoy.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return resultRepository.criticosEntre(desde, hasta).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    public List<ResultResponse> historial(Long idPaciente, String codigoPrueba, int limite) {
        int tope = limite <= 0 ? 12 : Math.min(limite, 100);
        return resultRepository.historialDe(idPaciente, codigoPrueba, PageRequest.of(0, tope))
                .stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Arma el resultado y lo interpreta frente a los rangos.
     *
     * <p>La interpretacion se calcula aqui y no en la entidad porque depende
     * de consultar el catalogo, que es trabajo del servicio.
     */
    private Result construir(Sample muestra, TestCatalog prueba, Double valorNumerico,
                             String valorTexto, String origen, String usuario,
                             String idDocumentoCrudo) {
        Result resultado = new Result(
                muestra,
                prueba.getCodigo(),
                prueba.getNombre(),
                valorNumerico,
                valorTexto,
                prueba.getUnidad(),
                origen,
                usuario);

        var interpretacion = validationService.interpretar(
                prueba.getCodigo(), valorNumerico,
                muestra.getSexoPaciente(), muestra.getEdadPaciente());

        resultado.interpretar(interpretacion.interpretacion(), interpretacion.rangoReferencia());

        if (idDocumentoCrudo != null) {
            resultado.vincularDocumentoCrudo(idDocumentoCrudo);
        }

        return resultado;
    }

    /**
     * Resultado que llego de un equipo.
     *
     * <p>Se interpreta con el flag que envio el analizador como dato
     * adicional, no como verdad: los limites del laboratorio son los que
     * valen, y el flag solo se respeta cuando marca algo critico que los
     * propios no detectan.
     */
    private Result construirDesdeEquipo(Sample muestra, TestCatalog prueba,
                                        Hl7ParsedResult.Observacion observacion,
                                        String equipo, String idDocumento) {
        // En una prueba cuantitativa no se guarda el texto del equipo como
        // valor: el numero ya lo recoge valorNumerico y repetirlo haria que
        // el informe mostrara el valor dos veces.
        String valorTexto = prueba.isCuantitativa() && observacion.esNumerica()
                ? null
                : observacion.valorTexto();

        Result resultado = new Result(
                muestra,
                prueba.getCodigo(),
                prueba.getNombre(),
                observacion.valorNumerico(),
                valorTexto,
                // La unidad del catalogo manda sobre la del equipo: es la que
                // corresponde al rango de referencia con el que se compara.
                prueba.getUnidad() == null ? observacion.unidad() : prueba.getUnidad(),
                ORIGEN_EQUIPO,
                equipo);

        var interpretacion = validationService.interpretarConFlag(
                prueba.getCodigo(), observacion.valorNumerico(), observacion.flagAnormal(),
                muestra.getSexoPaciente(), muestra.getEdadPaciente());

        resultado.interpretar(interpretacion.interpretacion(), interpretacion.rangoReferencia());
        resultado.vincularDocumentoCrudo(idDocumento);

        return resultado;
    }

    /**
     * Guarda la trama cruda, o recupera la ya guardada si es un reenvio.
     *
     * <p>Un analizador que no recibe el ACK a tiempo reenvia el mismo mensaje.
     * La coleccion tiene un indice unico por muestra y control id, de modo que
     * insertar otra vez fallaria con DuplicateKeyException y el equipo
     * recibiria un ACK de error por algo que ya esta guardado. Se detecto asi
     * contra el stack real.
     *
     * <p>El documento existente no se sobrescribe: un reenvio trae lo mismo
     * por definicion, y conservar el primero deja la hora de recepcion
     * original, que es la que vale para auditar.
     */
    private ResultDocument guardarTramaCruda(Sample muestra, Hl7ParsedResult parseado,
                                             String tramaOriginal) {
        ResultDocument nuevo = ResultDocument.deEquipo(
                muestra.getId(),
                muestra.getCodigo(),
                muestra.getIdOrden(),
                muestra.getIdPaciente(),
                parseado.equipo(),
                parseado.mensajeId(),
                tramaOriginal == null ? reconstruirTrama(parseado) : tramaOriginal,
                parseado.formato(),
                parseado.camposCrudos());

        return documentoRepository
                .findByIdMuestraAndIdMensaje(nuevo.idMuestra(), nuevo.idMensaje())
                .map(existente -> {
                    log.info("El equipo {} reenvio el mensaje {} de la muestra {}; "
                                    + "se reutiliza el documento recibido el {}",
                            parseado.equipo(), nuevo.idMensaje(), muestra.getCodigo(),
                            existente.fechaRecepcion());
                    return existente;
                })
                .orElseGet(() -> documentoRepository.save(nuevo));
    }

    private Sample buscarMuestra(Long idMuestra) {
        return sampleRepository.findById(idMuestra)
                .orElseThrow(() -> LaboratoryException.muestraNoEncontrada(idMuestra));
    }

    private void exigirQueAdmitaResultados(Sample muestra) {
        if (!muestra.admiteResultados()) {
            throw LaboratoryException.muestraNoAdmiteResultados(
                    muestra.getCodigo(), muestra.getEstado().name());
        }
    }

    private TestCatalog buscarPruebaActiva(String codigo) {
        TestCatalog prueba = catalogoRepository.findById(codigo)
                .orElseThrow(() -> LaboratoryException.pruebaNoEncontrada(codigo));
        if (!prueba.isActiva()) {
            throw LaboratoryException.pruebaInactiva(codigo);
        }
        return prueba;
    }

    /**
     * Una prueba cuantitativa sin numero no se puede comparar con su rango ni
     * detectar como critica, de modo que no serviria de nada informarla.
     */
    private void validarValores(ResultEntryRequest peticion, TestCatalog prueba) {
        if (!peticion.tieneAlgunValor()) {
            throw LaboratoryException.valorRequerido();
        }
        if (prueba.isCuantitativa() && peticion.valorNumerico() == null) {
            throw LaboratoryException.valorNumericoRequerido(prueba.getCodigo());
        }
    }

    /**
     * Reconstruye un texto legible de la trama a partir de los campos crudos.
     *
     * <p>Solo se usa como ultimo recurso, cuando quien llama no aporto la
     * trama literal. Es una representacion de los campos, suficiente para
     * auditar que envio el equipo, pero no sirve para reprocesar la trama tal
     * como llego.
     */
    private String reconstruirTrama(Hl7ParsedResult parseado) {
        StringBuilder texto = new StringBuilder();
        parseado.camposCrudos().forEach((campo, valor) ->
                texto.append(campo).append('=').append(valor).append('\n'));
        return texto.toString();
    }

    /**
     * Publica {@code result.registered} y, si procede, {@code result.critical}.
     *
     * <p>Siempre tras el commit: anunciar un resultado que un rollback
     * posterior deja sin existir haria que notification-service avisara al
     * medico de un valor critico inventado.
     */
    private void publicarTrasCommit(Result resultado, Sample muestra, String requestId) {
        Runnable publicacion = () -> {
            eventProducer.publicarRegistrado(resultado, muestra, requestId);
            if (resultado.isCritico()) {
                eventProducer.publicarCritico(resultado, muestra, requestId);
                criticalValueService.registrarAviso(resultado, muestra, requestId);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publicacion.run();
                        }
                    });
        } else {
            publicacion.run();
        }
    }
}
