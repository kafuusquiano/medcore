package com.medcore.medical.service.impl;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.request.LabOrderRequest;
import com.medcore.medical.domain.dto.response.LabOrderResponse;
import com.medcore.medical.domain.entity.LabOrder;
import com.medcore.medical.domain.entity.LabOrderTest;
import com.medcore.medical.domain.entity.MedicalAttention;
import com.medcore.medical.domain.enums.LabOrderPriority;
import com.medcore.medical.event.producer.LabOrderEventProducer;
import com.medcore.medical.exception.MedicalException;
import com.medcore.medical.mapper.LabOrderMapper;
import com.medcore.medical.repository.LabOrderRepository;
import com.medcore.medical.repository.MedicalAttentionRepository;
import com.medcore.medical.service.LabOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LabOrderServiceImpl implements LabOrderService {

    private static final Logger log = LoggerFactory.getLogger(LabOrderServiceImpl.class);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LabOrderRepository orderRepository;
    private final MedicalAttentionRepository attentionRepository;
    private final LabOrderEventProducer eventProducer;
    private final LabOrderMapper mapper;

    public LabOrderServiceImpl(LabOrderRepository orderRepository,
                               MedicalAttentionRepository attentionRepository,
                               LabOrderEventProducer eventProducer,
                               LabOrderMapper mapper) {
        this.orderRepository = orderRepository;
        this.attentionRepository = attentionRepository;
        this.eventProducer = eventProducer;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public LabOrderResponse emitir(Long idAtencion, LabOrderRequest request, String requestId) {
        if (request.pruebas() == null || request.pruebas().isEmpty()) {
            throw MedicalException.ordenSinPruebas();
        }

        MedicalAttention atencion = attentionRepository.findById(idAtencion)
                .orElseThrow(() -> MedicalException.atencionNoEncontrada(idAtencion));

        // Solo una atencion abierta genera ordenes: una ya finalizada es un
        // registro cerrado y anadirle una orden lo alteraria.
        if (!atencion.getEstado().admiteCambios()) {
            throw MedicalException.atencionCerrada();
        }

        LabOrder orden = new LabOrder(
                generarCodigo(),
                atencion,
                request.prioridad() == null ? LabOrderPriority.RUTINA : request.prioridad(),
                request.observaciones());

        // Se descartan los codigos repetidos dentro de la misma peticion:
        // el indice ux_order_test los rechazaria con un error tecnico.
        Set<String> vistos = new HashSet<>();
        for (var prueba : request.pruebas()) {
            if (!vistos.add(prueba.codigo())) {
                log.debug("Prueba {} repetida en la peticion; se ignora", prueba.codigo());
                continue;
            }
            orden.agregarPrueba(new LabOrderTest(
                    prueba.codigo(), prueba.nombre(), prueba.indicaciones()));
        }

        LabOrder guardada = orderRepository.save(orden);
        log.info("Orden {} emitida en la atencion {} con {} pruebas, prioridad {}",
                guardada.getCodigo(), idAtencion, guardada.getPruebas().size(),
                guardada.getPrioridad());

        // Tras el commit: publicarlo dentro permitiria que laboratory-service
        // abra una orden que luego revierte.
        trasCommit(() -> eventProducer.publicarCreada(guardada, requestId));

        return mapper.aRespuesta(guardada);
    }

    /**
     * Codigo legible y correlativo dentro del dia, por ejemplo
     * ORD-20261029-0007.
     *
     * <p>Limitacion conocida: con dos instancias del servicio, dos ordenes
     * emitidas en el mismo instante podrian calcular el mismo correlativo.
     * La restriccion UNIQUE sobre codigo lo rechazaria y la segunda fallaria.
     * Para escalar habria que usar una secuencia de Postgres.
     */
    private String generarCodigo() {
        long correlativo = orderRepository.contarEmitidasHoy() + 1;
        return "ORD-" + LocalDate.now().format(FORMATO_FECHA)
                + "-" + String.format("%04d", correlativo);
    }

    @Override
    @Transactional(readOnly = true)
    public LabOrderResponse obtener(Long id) {
        return mapper.aRespuesta(orderRepository.findWithPruebasById(id)
                .orElseThrow(() -> MedicalException.ordenNoEncontrada(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public LabOrderResponse obtenerPorCodigo(String codigo) {
        return mapper.aRespuesta(orderRepository.findByCodigo(codigo)
                .orElseThrow(() -> MedicalException.ordenNoEncontradaPorCodigo(codigo)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabOrderResponse> ordenesDeAtencion(Long idAtencion) {
        return orderRepository.findByAtencionId(idAtencion).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<LabOrderResponse> ordenesDePaciente(Long idPaciente, Pageable pageable) {
        return PageableResponse.de(
                orderRepository.findByIdPacienteOrderByFechaEmisionDesc(idPaciente, pageable),
                mapper::aRespuesta);
    }

    @Override
    @Transactional
    public LabOrderResponse anular(Long id, String motivo) {
        LabOrder orden = orderRepository.findWithPruebasById(id)
                .orElseThrow(() -> MedicalException.ordenNoEncontrada(id));

        if (!orden.getEstado().admiteAnulacion()) {
            // Despues de tomar la muestra ya se pincho al paciente y hay
            // material en proceso: anularla dejaria al laboratorio con una
            // muestra sin orden que la justifique.
            throw MedicalException.ordenNoAnulable(orden.getEstado().name());
        }

        orden.anular(motivo);
        log.info("Orden {} anulada: {}", orden.getCodigo(), motivo);

        return mapper.aRespuesta(orderRepository.save(orden));
    }

    private void trasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            accion.run();
                        }
                    });
        } else {
            accion.run();
        }
    }
}
