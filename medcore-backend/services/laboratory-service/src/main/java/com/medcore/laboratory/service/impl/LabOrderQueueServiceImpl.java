package com.medcore.laboratory.service.impl;

import com.medcore.commons.event.LabOrderEvent;
import com.medcore.laboratory.domain.dto.response.PendingOrderResponse;
import com.medcore.laboratory.domain.entity.PendingLabOrder;
import com.medcore.laboratory.domain.entity.TestCatalog;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.repository.PendingLabOrderRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.service.LabOrderQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LabOrderQueueServiceImpl implements LabOrderQueueService {

    private static final Logger log = LoggerFactory.getLogger(LabOrderQueueServiceImpl.class);

    private static final int LIMITE_COLA = 200;

    private final PendingLabOrderRepository ordenRepository;
    private final TestCatalogRepository catalogoRepository;

    public LabOrderQueueServiceImpl(PendingLabOrderRepository ordenRepository,
                                    TestCatalogRepository catalogoRepository) {
        this.ordenRepository = ordenRepository;
        this.catalogoRepository = catalogoRepository;
    }

    @Override
    @Transactional
    public void encolar(LabOrderEvent evento) {
        // Si la orden ya esta en la cola se trata de un reenvio del mismo
        // evento. No se sobrescribe: una orden ya atendida volveria a
        // aparecer como pendiente y el tecnico pediria un segundo tubo.
        if (ordenRepository.existsById(evento.idOrden())) {
            log.debug("La orden {} ya estaba en la cola; se ignora el reenvio [{}]",
                    evento.codigoOrden(), evento.requestId());
            return;
        }

        PendingLabOrder orden = new PendingLabOrder(
                evento.idOrden(),
                evento.codigoOrden(),
                evento.idAtencion(),
                evento.idPaciente(),
                evento.dniPaciente(),
                evento.nombrePaciente(),
                evento.correoPaciente(),
                evento.idMedico(),
                evento.nombreMedico(),
                evento.prioridad(),
                evento.observaciones(),
                evento.pruebas(),
                evento.occurredAt());

        ordenRepository.save(orden);

        log.info("Orden {} encolada con {} pruebas, prioridad {} [{}]",
                evento.codigoOrden(), evento.pruebas().size(),
                evento.prioridad(), evento.requestId());
    }

    @Override
    public List<PendingOrderResponse> pendientes(int limite) {
        int tope = limite <= 0 ? 50 : Math.min(limite, LIMITE_COLA);
        return ordenRepository.cola(PageRequest.of(0, tope)).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public List<PendingOrderResponse> pendientesDeDni(String dni) {
        return ordenRepository.findByDniPacienteAndAtendidaFalseOrderByFechaEventoAsc(dni).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public PendingOrderResponse obtenerPorId(Long idOrden) {
        return ordenRepository.findById(idOrden)
                .map(this::aRespuesta)
                .orElseThrow(() -> LaboratoryException.ordenNoEncontrada(idOrden));
    }

    @Override
    public Resumen resumen() {
        return new Resumen(
                ordenRepository.countByAtendidaFalse(),
                ordenRepository.contarUrgentesPendientes());
    }

    private PendingOrderResponse aRespuesta(PendingLabOrder orden) {
        List<PendingOrderResponse.PruebaPendiente> pruebas = orden.getPruebas().stream()
                .map(this::aPruebaPendiente)
                .toList();

        return new PendingOrderResponse(
                orden.getIdOrden(),
                orden.getCodigoOrden(),
                orden.getIdAtencion(),
                orden.getIdPaciente(),
                orden.getDniPaciente(),
                orden.getNombrePaciente(),
                orden.getIdMedico(),
                orden.getNombreMedico(),
                orden.getPrioridad(),
                orden.esUrgente(),
                orden.getObservaciones(),
                pruebas,
                Duration.between(orden.getFechaEvento(), Instant.now()).toHours(),
                orden.getFechaEvento());
    }

    /**
     * Completa la prueba con lo que el catalogo sabe de ella.
     *
     * <p>Cuando el codigo no esta en el catalogo se devuelve igualmente con
     * {@code enCatalogo} en false, en lugar de omitirla: el tecnico tiene que
     * ver que el medico pidio algo que el laboratorio no reconoce para poder
     * avisar, y ocultarlo haria que el tubo se procesara incompleto sin que
     * nadie lo notara.
     */
    private PendingOrderResponse.PruebaPendiente aPruebaPendiente(String codigo) {
        Optional<TestCatalog> enCatalogo = catalogoRepository.findById(codigo);

        if (enCatalogo.isEmpty()) {
            return new PendingOrderResponse.PruebaPendiente(
                    codigo, codigo, null, null,
                    "Codigo no reconocido por el laboratorio", false);
        }

        TestCatalog prueba = enCatalogo.get();
        return new PendingOrderResponse.PruebaPendiente(
                prueba.getCodigo(),
                prueba.getNombre(),
                prueba.getArea(),
                prueba.getTipoMuestra(),
                prueba.getIndicaciones(),
                true);
    }
}
