package com.medcore.analytics.service.impl;

import com.medcore.analytics.domain.document.DiagnosisRecord;
import com.medcore.analytics.repository.DiagnosisRecordRepository;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.analytics.repository.projection.ZonaProjection;
import com.medcore.analytics.service.ClinicalSurveillanceService;
import com.medcore.commons.event.DiagnosisEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ClinicalSurveillanceServiceImpl implements ClinicalSurveillanceService {

    private static final Logger log =
            LoggerFactory.getLogger(ClinicalSurveillanceServiceImpl.class);

    private final DiagnosisRecordRepository repository;

    public ClinicalSurveillanceServiceImpl(DiagnosisRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public DiagnosisRecord registrar(DiagnosisEvent evento) {
        String id = String.valueOf(evento.idDiagnostico());

        // Idempotencia: Kafka entrega al menos una vez, y un caso contado dos
        // veces puede disparar un brote que no existe.
        var existente = repository.findById(id);
        if (existente.isPresent()) {
            log.debug("El diagnostico {} ya estaba registrado; se ignora el reenvio [{}]",
                    evento.idDiagnostico(), evento.requestId());
            return existente.get();
        }

        DiagnosisRecord registro = DiagnosisRecord.de(evento);

        try {
            DiagnosisRecord guardado = repository.save(registro);

            if (guardado.notificable()) {
                // Las de notificacion obligatoria van al log en WARN aunque sea
                // un solo caso: no esperan a ningun umbral, y alguien tiene que
                // mandarlas al MINSA hoy.
                log.warn("DIAGNOSTICO NOTIFICABLE | {} ({}) | zona={} grupo={} [{}]",
                        guardado.codigoCie10(), guardado.descripcionCie10(),
                        guardado.zona(), guardado.grupoEdad(), evento.requestId());
            } else {
                log.debug("Diagnostico registrado: {} en {} ({}) [{}]",
                        guardado.codigoCie10(), guardado.zona(),
                        guardado.grupoEdad(), evento.requestId());
            }

            return guardado;

        } catch (DuplicateKeyException e) {
            // Otro consumidor del mismo grupo llego primero. No es un error.
            log.debug("El diagnostico {} lo registro otra ejecucion a la vez [{}]",
                    evento.idDiagnostico(), evento.requestId());
            return repository.findById(id).orElse(registro);
        }
    }

    @Override
    public List<ConteoPorClave> morbilidad(LocalDate desde, LocalDate hasta) {
        return repository.morbilidad(desde, hasta);
    }

    @Override
    public List<ConteoPorClave> porGrupoDeEdad(LocalDate desde, LocalDate hasta) {
        return repository.casosPorGrupoDeEdad(desde, hasta);
    }

    @Override
    public List<ConteoPorClave> porZona(LocalDate desde, LocalDate hasta) {
        return repository.casosPorZona(desde, hasta);
    }

    @Override
    public List<ConteoPorFecha> curvaEpidemica(LocalDate desde, LocalDate hasta) {
        return repository.casosPorDia(desde, hasta);
    }

    @Override
    public List<DiagnosisRecord> notificables(LocalDate desde, LocalDate hasta) {
        return repository.notificables(desde, hasta);
    }

    @Override
    public List<DiagnosisRecord> casosDe(String zona, String codigoCie10,
                                         LocalDate desde, LocalDate hasta) {
        return repository.casosDe(zona, codigoCie10, desde, hasta);
    }

    @Override
    public List<String> zonas() {
        return repository.zonasConCasos().stream()
                .map(ZonaProjection::zona)
                .filter(zona -> zona != null && !zona.isBlank())
                .toList();
    }

    @Override
    public Resumen resumen(LocalDate desde, LocalDate hasta) {
        return new Resumen(
                repository.contarEntre(desde, hasta),
                repository.contarNotificablesEntre(desde, hasta));
    }
}
