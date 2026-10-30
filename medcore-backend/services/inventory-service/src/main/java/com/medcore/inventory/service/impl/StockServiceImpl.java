package com.medcore.inventory.service.impl;

import com.medcore.inventory.config.InventoryProperties;
import com.medcore.inventory.domain.dto.request.StockEntryRequest;
import com.medcore.inventory.domain.dto.response.StockResponse;
import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.enums.MovementType;
import com.medcore.inventory.exception.InventoryException;
import com.medcore.inventory.mapper.StockMapper;
import com.medcore.inventory.repository.MovementLogRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.service.ReagentService;
import com.medcore.inventory.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    private final StockRepository stockRepository;
    private final MovementLogRepository movementRepository;
    private final ReagentService reagentService;
    private final StockMapper mapper;
    private final InventoryProperties properties;

    public StockServiceImpl(StockRepository stockRepository,
                            MovementLogRepository movementRepository,
                            ReagentService reagentService,
                            StockMapper mapper,
                            InventoryProperties properties) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.reagentService = reagentService;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public StockResponse ingresar(StockEntryRequest peticion, String usuario, String requestId) {
        Reagent reactivo = reagentService.buscarEntidad(peticion.idReactivo());

        if (!reactivo.isActivo()) {
            throw InventoryException.reactivoInactivo(reactivo.getCodigo());
        }

        LocalDate ingreso = peticion.ingresoOHoy();
        if (peticion.fechaVencimiento() != null
                && peticion.fechaVencimiento().isBefore(ingreso)) {
            throw InventoryException.vencimientoAnteriorAlIngreso();
        }

        // Si el lote ya existe y sigue vigente, lo correcto es una ENTRADA
        // sobre el lote existente, no un lote nuevo: son las mismas unidades
        // con el mismo vencimiento, y duplicarlo partiria el historial en dos.
        var existente = stockRepository
                .findByReactivoIdAndLoteAndRetiradoFalse(reactivo.getId(), peticion.lote());
        if (existente.isPresent()) {
            throw InventoryException.loteDuplicado(peticion.lote(), reactivo.getCodigo());
        }

        Stock lote = new Stock(
                reactivo,
                peticion.lote(),
                peticion.cantidad(),
                peticion.fechaVencimiento(),
                ingreso,
                peticion.ubicacion(),
                peticion.observaciones());

        Stock guardado = stockRepository.save(lote);

        // El movimiento de ENTRADA se registra aqui, con el repositorio, y no
        // a traves de MovementService: ese toma el lote con bloqueo pesimista
        // para resolver la concurrencia entre tecnicos, y un lote que se acaba
        // de crear en esta misma transaccion no es visible para nadie mas.
        //
        // Lo que no se omite es el movimiento: la cantidad de un lote nunca
        // cambia sin uno que lo justifique, ni siquiera al crearlo. Si no, el
        // historial empezaria con unidades aparecidas de la nada.
        MovementLog entrada = new MovementLog(
                guardado,
                MovementType.ENTRADA,
                peticion.cantidad(),
                0,
                "Ingreso inicial del lote",
                usuario == null ? "sistema" : usuario,
                null);
        movementRepository.save(entrada);

        log.info("Lote {} de {} ingresado con {} unidades, vence {} [{}]",
                guardado.getLote(), reactivo.getCodigo(), peticion.cantidad(),
                peticion.fechaVencimiento(), requestId);

        return mapper.aRespuesta(guardado, properties.diasDeAviso());
    }

    @Override
    @Transactional
    public StockResponse retirar(Long id, String motivo, String requestId) {
        if (motivo == null || motivo.isBlank()) {
            throw InventoryException.motivoRequerido("retiro de lote");
        }

        Stock lote = buscarEntidad(id);

        if (lote.isRetirado()) {
            throw InventoryException.loteRetirado(lote.getLote());
        }

        lote.retirar(motivo);
        Stock guardado = stockRepository.save(lote);

        if (guardado.getCantidadActual() > 0) {
            // Retirar no pone la cantidad a cero: eso lo hace una MERMA, que
            // es la que deja constancia de cuantas unidades se perdieron.
            // Separarlo permite saber si un lote se retiro lleno o ya gastado.
            log.warn("El lote {} se retira con {} unidades sin dar de baja. "
                            + "Registre una MERMA para dejar constancia de la perdida [{}]",
                    guardado.getLote(), guardado.getCantidadActual(), requestId);
        }

        log.info("Lote {} retirado: {} [{}]", guardado.getLote(), motivo, requestId);
        return mapper.aRespuesta(guardado, properties.diasDeAviso());
    }

    @Override
    @Transactional
    public StockResponse reubicar(Long id, String ubicacion, String requestId) {
        Stock lote = buscarEntidad(id);
        lote.reubicar(ubicacion);
        Stock guardado = stockRepository.save(lote);

        log.info("Lote {} reubicado a {} [{}]", guardado.getLote(), ubicacion, requestId);
        return mapper.aRespuesta(guardado, properties.diasDeAviso());
    }

    @Override
    public StockResponse obtenerPorId(Long id) {
        return mapper.aRespuesta(buscarEntidad(id), properties.diasDeAviso());
    }

    @Override
    public Stock buscarEntidad(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> InventoryException.loteNoEncontrado(id));
    }

    @Override
    public Page<StockResponse> listarVigentes(Pageable pageable) {
        return stockRepository.findByRetiradoFalseOrderByFechaVencimientoAsc(pageable)
                .map(this::aRespuesta);
    }

    @Override
    public List<StockResponse> lotesDeReactivo(Long idReactivo) {
        return stockRepository
                .findByReactivoIdAndRetiradoFalseOrderByFechaVencimientoAsc(idReactivo).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public List<StockResponse> lotesConsumibles(Long idReactivo) {
        return stockRepository.lotesConsumibles(idReactivo).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public List<StockResponse> vencidos() {
        return stockRepository.lotesVencidos().stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public List<StockResponse> porVencer() {
        LocalDate limite = LocalDate.now().plusDays(properties.diasDeAviso());
        return stockRepository.lotesPorVencer(limite).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public int totalDisponibleDe(Long idReactivo) {
        return stockRepository.totalDisponibleDe(idReactivo);
    }

    private StockResponse aRespuesta(Stock lote) {
        return mapper.aRespuesta(lote, properties.diasDeAviso());
    }
}
