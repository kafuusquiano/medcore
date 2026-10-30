package com.medcore.laboratory.service.impl;

import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.domain.entity.CriticalValue;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.mapper.ResultMapper;
import com.medcore.laboratory.repository.CriticalValueRepository;
import com.medcore.laboratory.repository.ResultRepository;
import com.medcore.laboratory.service.CriticalValueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CriticalValueServiceImpl implements CriticalValueService {

    private static final Logger log = LoggerFactory.getLogger(CriticalValueServiceImpl.class);

    private final CriticalValueRepository criticoRepository;
    private final ResultRepository resultRepository;
    private final ResultMapper mapper;

    public CriticalValueServiceImpl(CriticalValueRepository criticoRepository,
                                    ResultRepository resultRepository,
                                    ResultMapper mapper) {
        this.criticoRepository = criticoRepository;
        this.resultRepository = resultRepository;
        this.mapper = mapper;
    }

    @Override
    public void registrarAviso(Result resultado, Sample muestra, String requestId) {
        String indicacion = criticoRepository.findByCodigoPrueba(resultado.getCodigoPrueba())
                .map(CriticalValue::getIndicacion)
                .orElse("Avisar al medico tratante de inmediato");

        // Nivel WARN y en una sola linea con todo el detalle: es la traza que
        // el laboratorio necesita si el correo o el mensaje de
        // notification-service fallan y hay que demostrar que el sistema
        // detecto el valor, y cuando.
        log.warn("VALOR CRITICO | muestra={} paciente={} ({}) prueba={} valor={} {} "
                        + "interpretacion={} rango={} medico={} requestId={} | {}",
                muestra.getCodigo(),
                muestra.getNombrePaciente(),
                muestra.getDniPaciente(),
                resultado.getCodigoPrueba(),
                resultado.getValorNumerico() == null
                        ? resultado.getValorTexto()
                        : resultado.getValorNumerico(),
                resultado.getUnidad() == null ? "" : resultado.getUnidad(),
                resultado.getInterpretacion(),
                resultado.getRangoReferencia(),
                muestra.getIdMedico(),
                requestId,
                indicacion);
    }

    @Override
    public Optional<Limites> limitesDe(String codigoPrueba) {
        return criticoRepository.findByCodigoPrueba(codigoPrueba)
                .map(critico -> new Limites(
                        critico.getCodigoPrueba(),
                        critico.getLimiteInferior(),
                        critico.getLimiteSuperior(),
                        critico.getIndicacion()));
    }

    @Override
    public List<ResultResponse> criticosDeHoy() {
        LocalDate hoy = LocalDate.now();
        Instant desde = hoy.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return resultRepository.criticosEntre(desde, hasta).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    public long totalHistorico() {
        return resultRepository.countByCriticoTrue();
    }
}
