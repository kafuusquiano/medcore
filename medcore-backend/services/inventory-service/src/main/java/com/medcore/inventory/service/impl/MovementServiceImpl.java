package com.medcore.inventory.service.impl;

import com.medcore.inventory.domain.dto.request.MovementRequest;
import com.medcore.inventory.domain.dto.response.MovementResponse;
import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.enums.MovementType;
import com.medcore.inventory.event.producer.InventoryEventProducer;
import com.medcore.inventory.exception.InventoryException;
import com.medcore.inventory.mapper.StockMapper;
import com.medcore.inventory.repository.MovementLogRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.service.MovementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MovementServiceImpl implements MovementService {

    private static final Logger log = LoggerFactory.getLogger(MovementServiceImpl.class);

    private final StockRepository stockRepository;
    private final MovementLogRepository movementRepository;
    private final InventoryEventProducer eventProducer;
    private final StockMapper mapper;

    public MovementServiceImpl(StockRepository stockRepository,
                               MovementLogRepository movementRepository,
                               InventoryEventProducer eventProducer,
                               StockMapper mapper) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.eventProducer = eventProducer;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public MovementResponse registrar(Long idStock, MovementRequest peticion,
                                      String usuario, String requestId) {
        validar(peticion);

        // Bloqueo pesimista: dos tecnicos pueden descontar del mismo lote a la
        // vez, y el segundo debe aplicar su movimiento sobre la cantidad ya
        // actualizada, no sobre la que leyo al empezar.
        Stock lote = stockRepository.buscarParaMover(idStock)
                .orElseThrow(() -> InventoryException.loteNoEncontrado(idStock));

        exigirLoteUtilizable(lote, peticion.tipo());

        MovementLog movimiento = aplicar(lote, peticion.tipo(), peticion.cantidad(),
                peticion.motivo(), usuario, peticion.codigoMuestra());

        log.info("{} de {} unidades en el lote {} de {}: {} -> {} [{}]",
                peticion.tipo(), peticion.cantidad(), lote.getLote(),
                movimiento.getCodigoReactivo(), movimiento.getCantidadAnterior(),
                movimiento.getCantidadResultante(), requestId);

        avisarSiQuedaBajoMinimo(lote.getReactivo(), requestId);

        return mapper.aRespuesta(movimiento);
    }

    @Override
    @Transactional
    public List<MovementResponse> consumir(Long idReactivo, int cantidad, String codigoMuestra,
                                           String usuario, String requestId) {
        if (cantidad <= 0) {
            throw InventoryException.cantidadInvalida();
        }

        List<Stock> lotes = stockRepository.lotesConsumibles(idReactivo);

        // Se comprueba el total ANTES de tocar nada: un consumo a medias
        // dejaria material apuntado como gastado sin haber podido hacer la
        // prueba, y habria que deshacerlo a mano.
        int disponible = lotes.stream().mapToInt(Stock::getCantidadActual).sum();
        if (disponible < cantidad) {
            String codigo = lotes.isEmpty()
                    ? String.valueOf(idReactivo)
                    : lotes.get(0).getReactivo().getCodigo();
            throw InventoryException.sinStockDisponible(codigo, cantidad);
        }

        List<MovementResponse> movimientos = new ArrayList<>();
        Reagent reactivo = lotes.get(0).getReactivo();
        int porRepartir = cantidad;

        for (Stock candidato : lotes) {
            if (porRepartir == 0) {
                break;
            }

            // Se vuelve a leer con bloqueo: la lista se obtuvo sin el, y entre
            // leerla y llegar aqui otro tecnico puede haber consumido del
            // mismo lote.
            Stock lote = stockRepository.buscarParaMover(candidato.getId())
                    .orElseThrow(() -> InventoryException.loteNoEncontrado(candidato.getId()));

            int disponibleEnLote = lote.getCantidadActual();
            if (disponibleEnLote <= 0) {
                continue;
            }

            int aTomar = Math.min(porRepartir, disponibleEnLote);

            MovementLog movimiento = aplicar(lote, MovementType.SALIDA, aTomar,
                    null, usuario, codigoMuestra);
            movimientos.add(mapper.aRespuesta(movimiento));

            porRepartir -= aTomar;
        }

        if (porRepartir > 0) {
            // Solo ocurre si otro consumo se adelanto entre la comprobacion
            // del total y el reparto. La transaccion se deshace entera.
            throw InventoryException.sinStockDisponible(reactivo.getCodigo(), cantidad);
        }

        log.info("Consumidas {} unidades de {} en {} lote(s) para la muestra {} [{}]",
                cantidad, reactivo.getCodigo(), movimientos.size(), codigoMuestra, requestId);

        avisarSiQuedaBajoMinimo(reactivo, requestId);

        return movimientos;
    }

    @Override
    public Page<MovementResponse> historialDeLote(Long idStock, Pageable pageable) {
        return movementRepository.findByStockIdOrderByFechaMovimientoDesc(idStock, pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public Page<MovementResponse> historialDeReactivo(String codigoReactivo, Pageable pageable) {
        return movementRepository
                .findByCodigoReactivoOrderByFechaMovimientoDesc(
                        codigoReactivo.trim().toUpperCase(), pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public Page<MovementResponse> historialCompleto(Pageable pageable) {
        return movementRepository.findAllByOrderByFechaMovimientoDesc(pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public List<MovementResponse> movimientosDeMuestra(String codigoMuestra) {
        return movementRepository.findByCodigoMuestraOrderByFechaMovimientoAsc(codigoMuestra)
                .stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    public int consumoUltimosDias(String codigoReactivo, int dias) {
        int ventana = dias <= 0 ? 30 : Math.min(dias, 365);
        Instant hasta = Instant.now();
        Instant desde = hasta.minus(ventana, ChronoUnit.DAYS);
        return movementRepository.consumoEntre(
                codigoReactivo.trim().toUpperCase(), desde, hasta);
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Aplica el movimiento al lote y deja su registro.
     *
     * <p>Las dos cosas van juntas y en este orden porque el registro necesita
     * la cantidad anterior: si se guardara primero el movimiento, habria que
     * leer la cantidad dos veces y una excepcion en medio dejaria un
     * movimiento sin su efecto.
     */
    private MovementLog aplicar(Stock lote, MovementType tipo, int cantidad,
                                String motivo, String usuario, String codigoMuestra) {
        int anterior = lote.getCantidadActual();
        int delta = tipo.deltaDe(cantidad);

        if (delta == 0) {
            throw InventoryException.cantidadInvalida();
        }

        // La entidad rechaza el movimiento si dejaria el lote en negativo. Se
        // traduce aqui al error de dominio para que el mensaje diga cuanto hay
        // y cuanto se pedia.
        try {
            lote.aplicarMovimiento(delta);
        } catch (IllegalArgumentException e) {
            throw InventoryException.stockInsuficiente(
                    lote.getLote(), anterior, Math.abs(delta));
        }

        stockRepository.save(lote);

        MovementLog movimiento = new MovementLog(
                lote, tipo, delta, anterior, motivo,
                usuario == null ? "sistema" : usuario, codigoMuestra);

        return movementRepository.save(movimiento);
    }

    private void validar(MovementRequest peticion) {
        if (peticion.cantidad() == null || peticion.cantidad() == 0) {
            throw InventoryException.cantidadInvalida();
        }

        // Solo el AJUSTE corrige en cualquier direccion. En el resto el signo
        // lo pone el tipo, de modo que una cantidad negativa indica que quien
        // llama entendio mal la operacion.
        if (peticion.cantidad() < 0 && !peticion.tipo().admiteCantidadNegativa()) {
            throw InventoryException.cantidadNegativaNoPermitida(peticion.tipo().name());
        }

        // Una merma, un ajuste o una devolucion sin motivo no sirven para
        // auditar nada. La salida por consumo no lo exige: su motivo es el
        // trabajo diario y se identifica con el codigo de la muestra.
        boolean exigeMotivo = peticion.tipo() != MovementType.ENTRADA
                && peticion.tipo() != MovementType.SALIDA;
        if (exigeMotivo && !peticion.tieneMotivo()) {
            throw InventoryException.motivoRequerido(peticion.tipo().name());
        }
    }

    /**
     * Comprueba que el lote admita el movimiento.
     *
     * <p>Un lote retirado no admite ninguno. Uno vencido solo admite los que
     * sacan material: consumirlo daria un resultado clinico que no se puede
     * respaldar, pero hay que poder mermarlo o devolverlo al proveedor, que es
     * justamente lo que toca hacer con el.
     */
    private void exigirLoteUtilizable(Stock lote, MovementType tipo) {
        if (lote.isRetirado()) {
            throw InventoryException.loteRetirado(lote.getLote());
        }

        if (lote.estaVencido() && !sacaMaterial(tipo)) {
            throw InventoryException.loteVencido(
                    lote.getLote(), String.valueOf(lote.getFechaVencimiento()));
        }
    }

    private boolean sacaMaterial(MovementType tipo) {
        return tipo == MovementType.MERMA
                || tipo == MovementType.DEVOLUCION
                || tipo == MovementType.AJUSTE;
    }

    /**
     * Publica {@code stock.low} si el movimiento dejo el reactivo bajo minimo.
     *
     * <p>Se comprueba tras cada movimiento y no solo en la revision nocturna:
     * si un consumo agota el reactivo a media manana, esperar hasta el dia
     * siguiente para avisar es esperar demasiado.
     *
     * <p>El total se recalcula con la consulta de agregacion, no con la
     * cantidad del lote: lo que importa es cuanto queda en TOTAL, porque puede
     * haber otros lotes del mismo reactivo.
     */
    private void avisarSiQuedaBajoMinimo(Reagent reactivo, String requestId) {
        int total = stockRepository.totalDisponibleDe(reactivo.getId());

        if (!reactivo.estaBajoMinimo(total)) {
            return;
        }

        log.warn("El reactivo {} queda con {} unidades, por debajo de su minimo de {} [{}]",
                reactivo.getCodigo(), total, reactivo.getStockMinimo(), requestId);

        publicarTrasCommit(() -> eventProducer.publicarStockBajo(reactivo, total, requestId));
    }

    /**
     * Publica el evento solo si la transaccion confirma.
     *
     * <p>Publicar dentro anunciaria un stock bajo que un rollback posterior
     * deja sin ocurrir, y el responsable del almacen recibiria un aviso de
     * algo que no paso.
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
