package com.medcore.medical.service.impl;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.response.Cie10Response;
import com.medcore.medical.domain.dto.response.DiagnosisResponse;
import com.medcore.medical.domain.entity.Cie10Code;
import com.medcore.medical.exception.MedicalException;
import com.medcore.medical.mapper.AttentionMapper;
import com.medcore.medical.repository.Cie10CodeRepository;
import com.medcore.medical.repository.DiagnosisRepository;
import com.medcore.medical.service.DiagnosisService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DiagnosisServiceImpl implements DiagnosisService {

    private final Cie10CodeRepository cie10Repository;
    private final DiagnosisRepository diagnosisRepository;
    private final AttentionMapper mapper;

    public DiagnosisServiceImpl(Cie10CodeRepository cie10Repository,
                                DiagnosisRepository diagnosisRepository,
                                AttentionMapper mapper) {
        this.cie10Repository = cie10Repository;
        this.diagnosisRepository = diagnosisRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<Cie10Response> buscarCie10(String texto, Pageable pageable) {
        var pagina = (texto == null || texto.isBlank())
                ? cie10Repository.findAll(pageable)
                : cie10Repository.buscar(texto.trim(), pageable);

        return PageableResponse.de(pagina, this::aRespuesta);
    }

    @Override
    @Transactional(readOnly = true)
    public Cie10Response obtenerCie10(String codigo) {
        return cie10Repository.findById(codigo)
                .map(this::aRespuesta)
                .orElseThrow(() -> MedicalException.cie10NoExiste(codigo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiagnosisResponse> notificablesEntre(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);

        return diagnosisRepository.notificablesEntre(
                        desde.atStartOfDay().toInstant(ZoneOffset.UTC),
                        // Se incluye el dia completo de "hasta": el usuario
                        // espera que un rango de un solo dia devuelva ese dia.
                        hasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConteoDiagnostico> conteoPorCodigo(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);

        return diagnosisRepository.conteoPorCodigo(
                        desde.atStartOfDay().toInstant(ZoneOffset.UTC),
                        hasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).stream()
                .map(fila -> new ConteoDiagnostico(
                        (String) fila[0], (String) fila[1], ((Number) fila[2]).longValue()))
                .toList();
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw MedicalException.rangoFechasInvalido();
        }
    }

    private Cie10Response aRespuesta(Cie10Code codigo) {
        return new Cie10Response(codigo.getCodigo(), codigo.getDescripcion(),
                codigo.getCapitulo(), codigo.isNotificable());
    }
}
