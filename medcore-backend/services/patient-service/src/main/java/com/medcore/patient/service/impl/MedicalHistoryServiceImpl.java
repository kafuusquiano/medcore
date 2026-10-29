package com.medcore.patient.service.impl;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.patient.domain.dto.response.MedicalHistoryResponse;
import com.medcore.patient.domain.entity.MedicalHistory;
import com.medcore.patient.domain.entity.Patient;
import com.medcore.patient.exception.PatientException;
import com.medcore.patient.mapper.MedicalHistoryMapper;
import com.medcore.patient.repository.MedicalHistoryRepository;
import com.medcore.patient.repository.PatientRepository;
import com.medcore.patient.service.MedicalHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MedicalHistoryServiceImpl implements MedicalHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MedicalHistoryServiceImpl.class);

    private final MedicalHistoryRepository historyRepository;
    private final PatientRepository patientRepository;
    private final MedicalHistoryMapper historyMapper;

    public MedicalHistoryServiceImpl(MedicalHistoryRepository historyRepository,
                                     PatientRepository patientRepository,
                                     MedicalHistoryMapper historyMapper) {
        this.historyRepository = historyRepository;
        this.patientRepository = patientRepository;
        this.historyMapper = historyMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<MedicalHistoryResponse> historialDe(Long idPaciente, Pageable pageable) {
        // Se comprueba que la ficha exista para distinguir "el paciente no
        // existe" de "el paciente aun no tiene episodios".
        if (!patientRepository.existsById(idPaciente)) {
            throw PatientException.noEncontrado(idPaciente);
        }

        return PageableResponse.de(
                historyRepository.findByPacienteIdOrderByFechaEpisodioDesc(idPaciente, pageable),
                historyMapper::aRespuesta);
    }

    @Override
    @Transactional
    public MedicalHistoryResponse registrarEpisodio(Long idPaciente, String origen,
                                                    String referenciaExterna, String titulo,
                                                    String descripcion, String codigoCie10,
                                                    Instant fechaEpisodio) {

        Patient paciente = patientRepository.findById(idPaciente)
                .orElseThrow(() -> PatientException.noEncontrado(idPaciente));

        // Idempotencia: el servicio de origen puede reintentar y el mismo
        // episodio no debe aparecer dos veces en la linea de tiempo.
        if (referenciaExterna != null && !referenciaExterna.isBlank()) {
            var existente = historyRepository
                    .findByOrigenAndReferenciaExterna(origen, referenciaExterna);
            if (existente.isPresent()) {
                log.debug("Episodio {}/{} ya registrado; se devuelve el existente",
                        origen, referenciaExterna);
                return historyMapper.aRespuesta(existente.get());
            }
        }

        MedicalHistory episodio = new MedicalHistory(
                paciente, origen, referenciaExterna, titulo, descripcion, codigoCie10, fechaEpisodio);

        MedicalHistory guardado = historyRepository.save(episodio);
        log.info("Episodio {} registrado en el historial del paciente {}",
                guardado.getId(), idPaciente);

        return historyMapper.aRespuesta(guardado);
    }
}
