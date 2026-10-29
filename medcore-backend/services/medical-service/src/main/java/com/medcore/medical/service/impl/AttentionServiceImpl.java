package com.medcore.medical.service.impl;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.request.AttentionRequest;
import com.medcore.medical.domain.dto.request.AttentionUpdateRequest;
import com.medcore.medical.domain.dto.request.DiagnosisRequest;
import com.medcore.medical.domain.dto.response.AttentionResponse;
import com.medcore.medical.domain.entity.Cie10Code;
import com.medcore.medical.domain.entity.Diagnosis;
import com.medcore.medical.domain.entity.MedicalAttention;
import com.medcore.medical.exception.MedicalException;
import com.medcore.medical.mapper.AttentionMapper;
import com.medcore.medical.repository.Cie10CodeRepository;
import com.medcore.medical.repository.MedicalAttentionRepository;
import com.medcore.medical.service.AttentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttentionServiceImpl implements AttentionService {

    private static final Logger log = LoggerFactory.getLogger(AttentionServiceImpl.class);

    private final MedicalAttentionRepository attentionRepository;
    private final Cie10CodeRepository cie10Repository;
    private final AttentionMapper mapper;

    public AttentionServiceImpl(MedicalAttentionRepository attentionRepository,
                                Cie10CodeRepository cie10Repository,
                                AttentionMapper mapper) {
        this.attentionRepository = attentionRepository;
        this.cie10Repository = cie10Repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AttentionResponse abrir(AttentionRequest request) {
        // Una cita produce como mucho una atencion viva. Se comprueba antes
        // para devolver un error claro, aunque el indice unico parcial
        // ux_attention_cita_viva lo impediria igualmente.
        if (request.idCita() != null) {
            attentionRepository.atencionVivaDeCita(request.idCita()).ifPresent(a -> {
                throw MedicalException.citaYaAtendida(request.idCita());
            });
        }

        MedicalAttention atencion = new MedicalAttention(
                request.idCita(), request.idPaciente(), request.dniPaciente(),
                request.nombrePaciente(), request.correoPaciente(),
                request.idMedico(), request.nombreMedico(), request.motivoConsulta());

        MedicalAttention guardada = attentionRepository.save(atencion);
        log.info("Atencion {} abierta para el paciente {} por el medico {}",
                guardada.getId(), request.idPaciente(), request.idMedico());

        return mapper.aRespuesta(guardada);
    }

    @Override
    @Transactional
    public AttentionResponse registrarHallazgos(Long id, AttentionUpdateRequest request) {
        if (!request.presionCoherente()) {
            throw new MedicalException("PRESION_INCOHERENTE",
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La presion sistolica debe ser mayor que la diastolica.");
        }

        MedicalAttention atencion = buscarConDiagnosticos(id);

        // La entidad valida que siga abierta; el handler traduce a 409.
        atencion.registrarHallazgos(request.anamnesis(), request.examenFisico(),
                request.tratamiento(), request.observaciones());

        atencion.registrarSignosVitales(request.temperatura(), request.presionSistolica(),
                request.presionDiastolica(), request.frecuenciaCardiaca(),
                request.pesoKg(), request.tallaCm());

        return mapper.aRespuesta(attentionRepository.save(atencion));
    }

    @Override
    @Transactional
    public AttentionResponse agregarDiagnostico(Long id, DiagnosisRequest request) {
        MedicalAttention atencion = buscarConDiagnosticos(id);

        // La descripcion y la marca de notificable salen del catalogo, no de
        // lo que envie el cliente: son datos de referencia.
        Cie10Code codigo = cie10Repository.findById(request.codigoCie10())
                .orElseThrow(() -> MedicalException.cie10NoExiste(request.codigoCie10()));

        boolean yaRegistrado = atencion.getDiagnosticos().stream()
                .anyMatch(d -> d.getCodigoCie10().equals(request.codigoCie10()));
        if (yaRegistrado) {
            throw MedicalException.diagnosticoDuplicado(request.codigoCie10());
        }

        if (request.principal()) {
            boolean yaHayPrincipal = atencion.getDiagnosticos().stream()
                    .anyMatch(Diagnosis::isPrincipal);
            if (yaHayPrincipal) {
                throw MedicalException.yaHayDiagnosticoPrincipal();
            }
        }

        Diagnosis diagnostico = new Diagnosis(
                codigo.getCodigo(), codigo.getDescripcion(), request.principal(),
                request.casoNuevo(), codigo.isNotificable(), request.observaciones());

        atencion.agregarDiagnostico(diagnostico);

        if (codigo.isNotificable()) {
            log.info("Diagnostico notificable {} registrado en la atencion {}",
                    codigo.getCodigo(), id);
        }

        return mapper.aRespuesta(attentionRepository.save(atencion));
    }

    @Override
    @Transactional
    public AttentionResponse finalizar(Long id) {
        MedicalAttention atencion = buscarConDiagnosticos(id);

        if (atencion.getDiagnosticos().isEmpty()) {
            // Se comprueba aqui para devolver un codigo de dominio claro en
            // lugar del IllegalStateException generico de la entidad.
            throw MedicalException.sinDiagnostico();
        }

        atencion.finalizar();
        log.info("Atencion {} finalizada con {} diagnosticos",
                id, atencion.getDiagnosticos().size());

        return mapper.aRespuesta(attentionRepository.save(atencion));
    }

    @Override
    @Transactional
    public AttentionResponse anular(Long id, String motivo) {
        MedicalAttention atencion = buscarConDiagnosticos(id);
        atencion.anular(motivo);
        log.info("Atencion {} anulada: {}", id, motivo);
        return mapper.aRespuesta(attentionRepository.save(atencion));
    }

    @Override
    @Transactional(readOnly = true)
    public AttentionResponse obtener(Long id) {
        return mapper.aRespuesta(buscarConDiagnosticos(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<AttentionResponse> atencionesDePaciente(Long idPaciente,
                                                                    Pageable pageable) {
        return PageableResponse.de(
                attentionRepository.findByIdPacienteOrderByFechaAtencionDesc(idPaciente, pageable),
                mapper::aRespuesta);
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<AttentionResponse> atencionesDeMedico(Long idMedico,
                                                                   Pageable pageable) {
        return PageableResponse.de(
                attentionRepository.findByIdMedicoOrderByFechaAtencionDesc(idMedico, pageable),
                mapper::aRespuesta);
    }

    /**
     * Carga la atencion con sus diagnosticos en una sola consulta: la
     * coleccion es perezosa y se necesita tanto para validar como para
     * construir la respuesta.
     */
    private MedicalAttention buscarConDiagnosticos(Long id) {
        return attentionRepository.findWithDiagnosticosById(id)
                .orElseThrow(() -> MedicalException.atencionNoEncontrada(id));
    }
}
