package com.medcore.inventory.service.impl;

import com.medcore.inventory.config.InventoryProperties;
import com.medcore.inventory.domain.dto.request.ReagentRequest;
import com.medcore.inventory.domain.dto.response.ReagentResponse;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.exception.InventoryException;
import com.medcore.inventory.mapper.ReagentMapper;
import com.medcore.inventory.repository.ReagentRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.service.ReagentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReagentServiceImpl implements ReagentService {

    private static final Logger log = LoggerFactory.getLogger(ReagentServiceImpl.class);

    private final ReagentRepository reagentRepository;
    private final StockRepository stockRepository;
    private final ReagentMapper mapper;
    private final InventoryProperties properties;

    public ReagentServiceImpl(ReagentRepository reagentRepository,
                              StockRepository stockRepository,
                              ReagentMapper mapper,
                              InventoryProperties properties) {
        this.reagentRepository = reagentRepository;
        this.stockRepository = stockRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public ReagentResponse crear(ReagentRequest peticion, String requestId) {
        String codigo = peticion.codigo().trim().toUpperCase();

        // Se comprueba antes de insertar para devolver un codigo de error
        // propio. La restriccion UNIQUE lo impediria igualmente, pero con un
        // mensaje de base de datos.
        if (reagentRepository.existsByCodigo(codigo)) {
            throw InventoryException.codigoDuplicado(codigo);
        }

        Reagent reactivo = new Reagent(
                codigo,
                peticion.nombre(),
                peticion.categoria(),
                peticion.unidadMedida(),
                peticion.proveedor(),
                peticion.minimoOCero(),
                peticion.stockOptimo(),
                peticion.exigeRefrigeracion(),
                peticion.observaciones());

        Reagent guardado = reagentRepository.save(reactivo);

        log.info("Reactivo {} creado: {} [{}]",
                guardado.getCodigo(), guardado.getNombre(), requestId);

        // Recien creado no tiene lotes, asi que el total es cero.
        return mapper.aRespuesta(guardado, 0);
    }

    @Override
    @Transactional
    public ReagentResponse actualizar(Long id, ReagentRequest peticion, String requestId) {
        Reagent reactivo = buscarEntidad(id);

        // El codigo no se toca: esta rotulado en la estanteria y copiado en
        // los movimientos ya registrados. Si llega otro, se avisa en el log en
        // lugar de ignorarlo en silencio.
        String codigoPedido = peticion.codigo().trim().toUpperCase();
        if (!codigoPedido.equals(reactivo.getCodigo())) {
            log.warn("Se intento cambiar el codigo de {} a {}; el codigo no es modificable [{}]",
                    reactivo.getCodigo(), codigoPedido, requestId);
        }

        reactivo.actualizar(
                peticion.nombre(),
                peticion.categoria(),
                peticion.unidadMedida(),
                peticion.proveedor(),
                peticion.minimoOCero(),
                peticion.stockOptimo(),
                peticion.exigeRefrigeracion(),
                peticion.observaciones());

        Reagent guardado = reagentRepository.save(reactivo);

        log.info("Reactivo {} actualizado [{}]", guardado.getCodigo(), requestId);
        return conTotal(guardado);
    }

    @Override
    @Transactional
    public ReagentResponse desactivar(Long id, String requestId) {
        Reagent reactivo = buscarEntidad(id);
        reactivo.desactivar();
        Reagent guardado = reagentRepository.save(reactivo);

        int total = stockRepository.totalDisponibleDe(id);
        if (total > 0) {
            // No se bloquea la baja: el reactivo puede estar descatalogado y
            // quedar material que hay que terminar de gastar. Pero conviene
            // que quede dicho, porque el reactivo ya no aparecera en los
            // listados activos.
            log.warn("El reactivo {} se da de baja con {} unidades todavia en almacen [{}]",
                    guardado.getCodigo(), total, requestId);
        }

        log.info("Reactivo {} desactivado [{}]", guardado.getCodigo(), requestId);
        return mapper.aRespuesta(guardado, total);
    }

    @Override
    @Transactional
    public ReagentResponse reactivar(Long id, String requestId) {
        Reagent reactivo = buscarEntidad(id);
        reactivo.reactivar();
        Reagent guardado = reagentRepository.save(reactivo);

        log.info("Reactivo {} reactivado [{}]", guardado.getCodigo(), requestId);
        return conTotal(guardado);
    }

    @Override
    public ReagentResponse obtenerPorId(Long id) {
        return fichaCompleta(buscarEntidad(id));
    }

    @Override
    public ReagentResponse obtenerPorCodigo(String codigo) {
        Reagent reactivo = reagentRepository.findByCodigo(codigo.trim().toUpperCase())
                .orElseThrow(() -> InventoryException.reactivoNoEncontradoPorCodigo(codigo));
        return fichaCompleta(reactivo);
    }

    @Override
    public Reagent buscarEntidad(Long id) {
        return reagentRepository.findById(id)
                .orElseThrow(() -> InventoryException.reactivoNoEncontrado(id));
    }

    @Override
    public Page<ReagentResponse> listarActivos(Pageable pageable) {
        return reagentRepository.findByActivoTrueOrderByCategoriaAscNombreAsc(pageable)
                .map(this::conTotal);
    }

    @Override
    public List<ReagentResponse> listarPorCategoria(String categoria) {
        return reagentRepository.findByCategoriaAndActivoTrueOrderByNombreAsc(categoria).stream()
                .map(this::conTotal)
                .toList();
    }

    @Override
    public List<ReagentResponse> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return reagentRepository.buscarPorNombre(texto.trim()).stream()
                .map(this::conTotal)
                .toList();
    }

    @Override
    public List<String> categorias() {
        return reagentRepository.categorias();
    }

    @Override
    public List<ReagentResponse> bajoMinimo() {
        return reagentRepository.reactivosBajoMinimo().stream()
                .map(this::conTotal)
                .toList();
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Vista del reactivo con su total, sin cargar los lotes.
     *
     * <p>El total se pide al repositorio con una consulta de agregacion en
     * lugar de sumar los lotes en memoria: en un listado de cien reactivos,
     * cargar los lotes de cada uno seria una consulta por fila.
     */
    private ReagentResponse conTotal(Reagent reactivo) {
        return mapper.aRespuesta(reactivo, stockRepository.totalDisponibleDe(reactivo.getId()));
    }

    /** Ficha completa: el reactivo con sus lotes vigentes. */
    private ReagentResponse fichaCompleta(Reagent reactivo) {
        var lotes = stockRepository
                .findByReactivoIdAndRetiradoFalseOrderByFechaVencimientoAsc(reactivo.getId());

        return mapper.aRespuesta(
                reactivo,
                stockRepository.totalDisponibleDe(reactivo.getId()),
                lotes,
                properties.diasDeAviso());
    }
}
