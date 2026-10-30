package com.medcore.inventory.service.impl;

import com.medcore.inventory.config.InventoryProperties;
import com.medcore.inventory.domain.dto.response.ReagentResponse;
import com.medcore.inventory.domain.dto.response.StockResponse;
import com.medcore.inventory.domain.entity.Reagent;
import com.medcore.inventory.domain.entity.Stock;
import com.medcore.inventory.event.producer.InventoryEventProducer;
import com.medcore.inventory.mapper.ReagentMapper;
import com.medcore.inventory.mapper.StockMapper;
import com.medcore.inventory.repository.ReagentRepository;
import com.medcore.inventory.repository.StockRepository;
import com.medcore.inventory.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlertServiceImpl implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);

    private final ReagentRepository reagentRepository;
    private final StockRepository stockRepository;
    private final InventoryEventProducer eventProducer;
    private final ReagentMapper reagentMapper;
    private final StockMapper stockMapper;
    private final InventoryProperties properties;

    public AlertServiceImpl(ReagentRepository reagentRepository,
                            StockRepository stockRepository,
                            InventoryEventProducer eventProducer,
                            ReagentMapper reagentMapper,
                            StockMapper stockMapper,
                            InventoryProperties properties) {
        this.reagentRepository = reagentRepository;
        this.stockRepository = stockRepository;
        this.eventProducer = eventProducer;
        this.reagentMapper = reagentMapper;
        this.stockMapper = stockMapper;
        this.properties = properties;
    }

    @Override
    public Resumen revisar(String requestId) {
        int dias = properties.diasDeAviso();

        List<Reagent> bajoMinimo = reagentRepository.reactivosBajoMinimo();
        List<Stock> vencidos = stockRepository.lotesVencidos();
        List<Stock> porVencer = stockRepository.lotesPorVencer(LocalDate.now().plusDays(dias));

        // Cada aviso se publica dentro de su propio try: si uno falla, los
        // demas tienen que salir igual. Una revision que se corta en el tercer
        // reactivo deja sin avisar a los veinte siguientes, y el responsable
        // del almacen no tiene forma de saber que falto.
        for (Reagent reactivo : bajoMinimo) {
            publicarConCuidado(
                    () -> eventProducer.publicarStockBajo(
                            reactivo,
                            stockRepository.totalDisponibleDe(reactivo.getId()),
                            requestId),
                    "stock.low de " + reactivo.getCodigo());
        }

        for (Stock lote : vencidos) {
            publicarConCuidado(
                    () -> eventProducer.publicarLoteVencido(lote, requestId),
                    "stock.expired del lote vencido " + lote.getLote());
        }

        for (Stock lote : porVencer) {
            publicarConCuidado(
                    () -> eventProducer.publicarLoteVencido(lote, requestId),
                    "stock.expired del lote por vencer " + lote.getLote());
        }

        Resumen resumen = new Resumen(bajoMinimo.size(), vencidos.size(), porVencer.size());

        if (resumen.total() == 0) {
            log.info("Revision de inventario sin novedades [{}]", requestId);
        } else {
            log.info("Revision de inventario: {} reactivos bajo minimo, {} lotes vencidos, "
                            + "{} por vencer en {} dias [{}]",
                    resumen.stockBajo(), resumen.vencidos(), resumen.porVencer(),
                    dias, requestId);
        }

        return resumen;
    }

    @Override
    public List<ReagentResponse> reactivosBajoMinimo() {
        return reagentRepository.reactivosBajoMinimo().stream()
                .map(reactivo -> reagentMapper.aRespuesta(
                        reactivo, stockRepository.totalDisponibleDe(reactivo.getId())))
                .toList();
    }

    @Override
    public List<StockResponse> lotesQueRequierenAtencion() {
        int dias = properties.diasDeAviso();

        List<StockResponse> atencion = new ArrayList<>();
        // Los vencidos primero: son los que hay que sacar de la estanteria hoy.
        stockRepository.lotesVencidos()
                .forEach(lote -> atencion.add(stockMapper.aRespuesta(lote, dias)));
        stockRepository.lotesPorVencer(LocalDate.now().plusDays(dias))
                .forEach(lote -> atencion.add(stockMapper.aRespuesta(lote, dias)));

        return atencion;
    }

    /**
     * Publica un aviso sin dejar que su fallo corte la revision.
     *
     * <p>El productor ya no propaga los fallos de Kafka, pero cargar los datos
     * del evento puede fallar por otros motivos, como un lote cuyo reactivo se
     * borro a mano. No perder el resto de la revision por eso es mas
     * importante que ese aviso concreto, que volvera a detectarse manana:
     * la situacion de cada lote se calcula cada vez y no se guarda.
     */
    private void publicarConCuidado(Runnable publicacion, String descripcion) {
        try {
            publicacion.run();
        } catch (RuntimeException e) {
            log.error("No se pudo publicar {}: {}", descripcion, e.getMessage(), e);
        }
    }
}
