package com.medcore.inventory.service.impl;

import com.medcore.commons.event.ResultEvent;
import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.domain.entity.TestReagentUsage;
import com.medcore.inventory.domain.enums.MovementType;
import com.medcore.inventory.exception.InventoryException;
import com.medcore.inventory.repository.MovementLogRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.repository.TestReagentUsageRepository;
import com.medcore.inventory.service.AutomaticConsumptionService;
import com.medcore.inventory.service.MovementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AutomaticConsumptionServiceImpl implements AutomaticConsumptionService {

    private static final Logger log =
            LoggerFactory.getLogger(AutomaticConsumptionServiceImpl.class);

    /** Lo que se apunta como motivo del movimiento automatico. */
    private static final String MOTIVO = "Consumo automatico por la prueba ";

    private final TestReagentUsageRepository usageRepository;
    private final StockRepository stockRepository;
    private final MovementLogRepository movementRepository;
    private final MovementService movementService;

    public AutomaticConsumptionServiceImpl(TestReagentUsageRepository usageRepository,
                                           StockRepository stockRepository,
                                           MovementLogRepository movementRepository,
                                           MovementService movementService) {
        this.usageRepository = usageRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.movementService = movementService;
    }

    @Override
    @Transactional
    public List<Descuento> descontarPor(ResultEvent evento) {
        if (evento.idResultado() == null || evento.codigoPrueba() == null) {
            log.warn("Resultado sin identificador o sin codigo de prueba; no se descuenta");
            return List.of();
        }

        // Idempotencia: Kafka entrega al menos una vez, y un descuento doble
        // deja el stock del sistema por debajo del real, pidiendo reposicion
        // de algo que hay. El indice unico parcial es la garantia en base.
        if (movementRepository.existsByIdResultado(evento.idResultado())) {
            log.debug("El resultado {} ya habia descontado reactivo; se ignora "
                    + "el reenvio [{}]", evento.idResultado(), evento.requestId());
            return List.of();
        }

        List<TestReagentUsage> lineas = usageRepository.activosDe(evento.codigoPrueba());

        if (lineas.isEmpty()) {
            // No es un error. El examen de orina se hace con tira reactiva, y
            // la tira no esta en el catalogo de reactivos.
            log.debug("La prueba {} no declara consumo de reactivo [{}]",
                    evento.codigoPrueba(), evento.requestId());
            return List.of();
        }

        List<Descuento> descuentos = new ArrayList<>(lineas.size());

        for (TestReagentUsage linea : lineas) {
            descuentos.add(descontarLinea(linea, evento));
        }

        List<Descuento> incompletos = descuentos.stream()
                .filter(d -> !d.completo())
                .toList();

        if (!incompletos.isEmpty()) {
            // Se avisa en WARN y no se lanza: que el almacen no cuadre no
            // puede bloquear la particion ni perder los resultados
            // siguientes, que pueden ser un valor critico. Queda aqui para
            // quien lleva el almacen.
            incompletos.forEach(d ->
                    log.warn("STOCK INSUFICIENTE | reactivo={} ({}) pedido={} "
                                    + "descontado={} falta={} | prueba={} muestra={} [{}]",
                            d.codigoReactivo(), d.nombreReactivo(), d.pedido(),
                            d.aplicado(), d.faltante(), evento.codigoPrueba(),
                            evento.codigoMuestra(), evento.requestId()));
        }

        return descuentos;
    }

    /**
     * Descuenta un reactivo, repartiendo entre lotes por vencimiento.
     *
     * <p>A diferencia del consumo manual, aqui NO se exige que alcance el
     * total: la prueba ya se hizo y el reactivo ya se gasto. Negarse a
     * apuntarlo porque el sistema cree que no habia no arregla el almacen,
     * solo esconde el descuadre.
     */
    private Descuento descontarLinea(TestReagentUsage linea, ResultEvent evento) {
        Reagent reactivo = linea.getReactivo();
        int pedido = linea.consumoDe(1);
        int porRepartir = pedido;

        List<Stock> lotes = stockRepository.lotesConsumibles(reactivo.getId());

        for (Stock candidato : lotes) {
            if (porRepartir == 0) {
                break;
            }

            // Se vuelve a leer con bloqueo: la lista se obtuvo sin el, y entre
            // leerla y llegar aqui otro consumo puede haber tomado del mismo
            // lote.
            Stock lote = stockRepository.buscarParaMover(candidato.getId())
                    .orElseThrow(() -> InventoryException.loteNoEncontrado(
                            candidato.getId()));

            int disponible = lote.getCantidadActual();
            if (disponible <= 0) {
                continue;
            }

            int aTomar = Math.min(porRepartir, disponible);
            int anterior = lote.getCantidadActual();

            lote.aplicarMovimiento(MovementType.SALIDA.deltaDe(aTomar));
            stockRepository.save(lote);

            movementRepository.save(new MovementLog(
                    lote, MovementType.SALIDA, MovementType.SALIDA.deltaDe(aTomar),
                    anterior, MOTIVO + evento.codigoPrueba(), "sistema",
                    evento.codigoMuestra(), evento.idResultado()));

            porRepartir -= aTomar;
        }

        int aplicado = pedido - porRepartir;

        if (aplicado > 0) {
            log.debug("Descontadas {} unidad(es) de {} por la prueba {} de la "
                            + "muestra {} [{}]",
                    aplicado, reactivo.getCodigo(), evento.codigoPrueba(),
                    evento.codigoMuestra(), evento.requestId());

            // Si el descuento lo dejo bajo minimo, hay que avisar: es el
            // sentido de llevar el stock al dia.
            movementService.revisarMinimo(reactivo.getId(), evento.requestId());
        }

        return new Descuento(reactivo.getCodigo(), reactivo.getNombre(),
                pedido, aplicado, porRepartir);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestReagentUsage> consumoDe(String codigoPrueba) {
        return usageRepository.activosDe(codigoPrueba);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> pruebasConConsumo() {
        return usageRepository.pruebasConConsumo();
    }
}
