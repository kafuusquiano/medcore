package com.medcore.laboratory.service.impl;

import com.medcore.laboratory.client.PatientClient;
import com.medcore.laboratory.client.PatientDto;
import com.medcore.laboratory.domain.dto.request.SampleRequest;
import com.medcore.laboratory.domain.dto.response.SampleResponse;
import com.medcore.laboratory.domain.entity.PendingLabOrder;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.enums.SampleStatus;
import com.medcore.laboratory.event.producer.SampleEventProducer;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.mapper.SampleMapper;
import com.medcore.laboratory.repository.PendingLabOrderRepository;
import com.medcore.laboratory.repository.ResultRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.repository.SampleRepository;
import com.medcore.laboratory.service.SampleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SampleServiceImpl implements SampleService {

    private static final Logger log = LoggerFactory.getLogger(SampleServiceImpl.class);

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Tope de filas que devuelve la cola de trabajo de una sola vez. */
    private static final int LIMITE_COLA = 100;

    /**
     * Rol con el que el servicio se identifica al consultar patient-service.
     *
     * <p>No se reenvia el rol del usuario que registro la muestra: la
     * consulta la hace el laboratorio por su cuenta, y de ese modo funciona
     * igual cuando la dispara un proceso automatico sin usuario detras.
     */
    private static final String ROL_LABORATORISTA = "LABORATORISTA";

    private final SampleRepository sampleRepository;
    private final ResultRepository resultRepository;
    private final PendingLabOrderRepository ordenRepository;
    private final TestCatalogRepository catalogoRepository;
    private final PatientClient patientClient;
    private final SampleEventProducer eventProducer;
    private final SampleMapper mapper;

    public SampleServiceImpl(SampleRepository sampleRepository,
                             ResultRepository resultRepository,
                             PendingLabOrderRepository ordenRepository,
                             TestCatalogRepository catalogoRepository,
                             PatientClient patientClient,
                             SampleEventProducer eventProducer,
                             SampleMapper mapper) {
        this.sampleRepository = sampleRepository;
        this.resultRepository = resultRepository;
        this.ordenRepository = ordenRepository;
        this.catalogoRepository = catalogoRepository;
        this.patientClient = patientClient;
        this.eventProducer = eventProducer;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public SampleResponse registrar(SampleRequest peticion, String usuario, String requestId) {
        PendingLabOrder orden = ordenRepository.findById(peticion.idOrden())
                .orElseThrow(() -> LaboratoryException.ordenNoEncontrada(peticion.idOrden()));

        // Se comprueba antes de insertar para devolver un codigo de error
        // propio. El indice unico parcial ux_sample_orden_viva lo impediria
        // igualmente, pero con un mensaje de base de datos.
        sampleRepository.muestraVivaDeOrden(orden.getIdOrden()).ifPresent(existente -> {
            throw LaboratoryException.ordenYaTieneMuestra(orden.getIdOrden());
        });

        // Los datos del paciente salen del evento, no de la peticion: pedirlos
        // de nuevo al tecnico abriria la puerta a que el tubo quede a nombre
        // de otra persona.
        PatientDto paciente = consultarPaciente(orden.getIdPaciente(), usuario, requestId);

        Sample muestra = new Sample(
                siguienteCodigo(),
                orden.getIdOrden(),
                orden.getCodigoOrden(),
                orden.getIdPaciente(),
                orden.getDniPaciente(),
                orden.getNombrePaciente(),
                orden.getCorreoPaciente(),
                paciente == null ? null : paciente.sexo(),
                paciente == null ? null : paciente.edad(),
                paciente == null ? null : paciente.zona(),
                orden.getIdMedico(),
                peticion.tipoMuestra(),
                // La urgencia de la orden manda: si el medico la marco
                // urgente, el tubo encabeza la cola aunque en recepcion no se
                // haya marcado la casilla.
                peticion.esUrgente() || orden.esUrgente(),
                peticion.tomadaPor() == null ? usuario : peticion.tomadaPor());

        if (peticion.observaciones() != null) {
            muestra.anotar(peticion.observaciones());
        }

        Sample guardada = sampleRepository.save(muestra);

        // La orden sale de la cola, pero la fila se conserva: deja constancia
        // de cuanto tardo el tubo en llegar.
        orden.marcarAtendida();
        ordenRepository.save(orden);

        log.info("Muestra {} registrada para la orden {} [{}]",
                guardada.getCodigo(), orden.getCodigoOrden(), requestId);

        publicarTrasCommit(() -> eventProducer.publicarRegistrada(guardada, requestId));

        return mapper.aRespuesta(guardada);
    }

    @Override
    @Transactional
    public SampleResponse iniciarProceso(Long id, String requestId) {
        Sample muestra = buscar(id);
        SampleStatus anterior = muestra.getEstado();

        if (!anterior.puedePasarA(SampleStatus.EN_PROCESO)) {
            throw LaboratoryException.transicionInvalida(anterior.name(), "EN_PROCESO");
        }

        muestra.iniciarProceso();
        Sample guardada = sampleRepository.save(muestra);

        log.info("Muestra {} en proceso [{}]", guardada.getCodigo(), requestId);
        return mapper.aRespuesta(guardada);
    }

    @Override
    @Transactional
    public SampleResponse marcarProcesada(Long id, String requestId) {
        Sample muestra = buscar(id);

        if (!muestra.getEstado().puedePasarA(SampleStatus.PROCESADA)) {
            throw LaboratoryException.transicionInvalida(muestra.getEstado().name(), "PROCESADA");
        }

        exigirTodasLasPruebasInformadas(muestra);

        muestra.marcarProcesada();
        Sample guardada = sampleRepository.save(muestra);

        log.info("Muestra {} procesada [{}]", guardada.getCodigo(), requestId);
        return mapper.aRespuesta(guardada, resultRepository.findByMuestraIdOrderByCodigoPrueba(id));
    }

    @Override
    @Transactional
    public SampleResponse rechazar(Long id, String motivo, String requestId) {
        if (motivo == null || motivo.isBlank()) {
            throw LaboratoryException.motivoRechazoRequerido();
        }

        Sample muestra = buscar(id);

        if (!muestra.getEstado().puedePasarA(SampleStatus.RECHAZADA)) {
            throw LaboratoryException.transicionInvalida(muestra.getEstado().name(), "RECHAZADA");
        }

        muestra.rechazar(motivo);
        Sample guardada = sampleRepository.save(muestra);

        // La orden vuelve a la cola para que se tome otro tubo.
        ordenRepository.findById(muestra.getIdOrden()).ifPresent(orden -> {
            orden.reabrir();
            ordenRepository.save(orden);
        });

        log.info("Muestra {} rechazada: {} [{}]", guardada.getCodigo(), motivo, requestId);

        publicarTrasCommit(() -> eventProducer.publicarRechazada(guardada, motivo, requestId));

        return mapper.aRespuesta(guardada);
    }

    @Override
    public SampleResponse obtenerPorId(Long id) {
        Sample muestra = buscar(id);
        return mapper.aRespuesta(muestra,
                resultRepository.findByMuestraIdOrderByCodigoPrueba(id));
    }

    @Override
    public SampleResponse obtenerPorCodigo(String codigo) {
        Sample muestra = buscarEntidadPorCodigo(codigo);
        return mapper.aRespuesta(muestra,
                resultRepository.findByMuestraIdOrderByCodigoPrueba(muestra.getId()));
    }

    @Override
    public Sample buscarEntidadPorCodigo(String codigo) {
        return sampleRepository.findByCodigo(codigo)
                .orElseThrow(() -> LaboratoryException.muestraNoEncontradaPorCodigo(codigo));
    }

    @Override
    public List<Result> resultadosDe(Long idMuestra) {
        return resultRepository.findByMuestraIdOrderByCodigoPrueba(idMuestra);
    }

    @Override
    public Page<SampleResponse> listarPorPaciente(Long idPaciente, Pageable pageable) {
        return sampleRepository.findByIdPacienteOrderByFechaTomaDesc(idPaciente, pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public Page<SampleResponse> listarPorEstado(SampleStatus estado, Pageable pageable) {
        return sampleRepository.findByEstadoOrderByUrgenteDescFechaTomaAsc(estado, pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public List<SampleResponse> colaDeTrabajo(int limite) {
        int tope = limite <= 0 ? 20 : Math.min(limite, LIMITE_COLA);
        return sampleRepository.colaDeTrabajo(PageRequest.of(0, tope)).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    // ---- Apoyo -------------------------------------------------------------

    private Sample buscar(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> LaboratoryException.muestraNoEncontrada(id));
    }

    /**
     * Comprueba que no quede ninguna prueba de la orden sin informar.
     *
     * <p>Cerrar la muestra antes dejaria pruebas pendientes y el medico
     * creeria que el laboratorio ya termino con ella.
     *
     * <p>Si la orden ya no esta en la tabla local, que solo ocurriria si
     * alguien la borro a mano, se deja cerrar: bloquear el cierre por un dato
     * ausente dejaria el tubo colgado para siempre.
     *
     * <p>Tampoco se exigen las pruebas cuyo codigo no esta en el catalogo. El
     * laboratorio no puede informar lo que no sabe hacer, asi que exigirlas
     * dejaria la muestra abierta indefinidamente. Se detecto con una orden
     * real que pedia un codigo que el catalogo no tenia. Queda en el log con
     * nivel WARN, porque la discrepancia hay que corregirla en el catalogo, y
     * el tecnico ya la ve marcada en la cola de ordenes.
     */
    private void exigirTodasLasPruebasInformadas(Sample muestra) {
        List<String> solicitadas = ordenRepository.findById(muestra.getIdOrden())
                .map(PendingLabOrder::getPruebas)
                .orElse(List.of());

        if (solicitadas.isEmpty()) {
            return;
        }

        Set<String> informadas = new HashSet<>();
        resultRepository.findByMuestraIdOrderByCodigoPrueba(muestra.getId())
                .forEach(r -> informadas.add(r.getCodigoPrueba()));

        List<String> pendientes = solicitadas.stream()
                .filter(codigo -> !informadas.contains(codigo))
                .toList();

        List<String> fueraDeCatalogo = pendientes.stream()
                .filter(codigo -> !catalogoRepository.existsById(codigo))
                .toList();

        if (!fueraDeCatalogo.isEmpty()) {
            log.warn("La orden {} pide pruebas que no estan en el catalogo del "
                            + "laboratorio: {}. No se exigen para cerrar la muestra {}, "
                            + "pero hay que alinear el catalogo.",
                    muestra.getCodigoOrden(), String.join(", ", fueraDeCatalogo),
                    muestra.getCodigo());
        }

        List<String> faltantes = pendientes.stream()
                .filter(catalogoRepository::existsById)
                .toList();

        if (!faltantes.isEmpty()) {
            throw new IllegalStateException(
                    "Faltan resultados de " + String.join(", ", faltantes)
                            + ". Informelos antes de cerrar la muestra.");
        }
    }

    /**
     * Consulta a patient-service el sexo, la edad y la zona.
     *
     * <p>Devuelve null si no responde: el fallback del cliente ya lo registro
     * en el log. La muestra se registra igual y se aplicara el rango de
     * referencia generico, menos preciso pero utilizable. Que el padron de
     * pacientes este caido no debe impedir recibir un tubo de sangre.
     */
    private PatientDto consultarPaciente(Long idPaciente, String usuario, String requestId) {
        try {
            var respuesta = patientClient.obtenerPorId(
                    idPaciente,
                    usuario == null ? "laboratory-service" : usuario,
                    ROL_LABORATORISTA,
                    requestId);
            return respuesta == null ? null : respuesta.data();
        } catch (RuntimeException e) {
            log.warn("No se pudo obtener el sexo y la edad del paciente {}: {}",
                    idPaciente, e.getMessage());
            return null;
        }
    }

    /**
     * Correlativo del dia: MUE-20261029-0012.
     *
     * <p>Se calcula contando las muestras del dia. Con una sola instancia del
     * servicio basta; al escalar a dos haria falta una secuencia de Postgres,
     * porque dos peticiones simultaneas pueden contar lo mismo. El indice
     * unico sobre codigo evita el duplicado en base, pero una de las dos
     * fallaria.
     */
    private String siguienteCodigo() {
        LocalDate hoy = LocalDate.now();
        Instant desde = hoy.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long delDia = sampleRepository.contarEntre(desde, hasta);
        return String.format("MUE-%s-%04d", hoy.format(FORMATO_DIA), delDia + 1);
    }

    /**
     * Publica el evento solo si la transaccion confirma.
     *
     * <p>Publicar dentro de la transaccion anunciaria una muestra que un
     * rollback posterior dejaria sin existir, y notification-service ya
     * habria avisado al paciente.
     */
    private void publicarTrasCommit(Runnable publicacion) {
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
