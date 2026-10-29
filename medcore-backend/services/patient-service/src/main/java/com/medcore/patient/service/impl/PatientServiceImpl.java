package com.medcore.patient.service.impl;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.patient.domain.dto.request.PatientRequest;
import com.medcore.patient.domain.dto.request.PatientSearchRequest;
import com.medcore.patient.domain.dto.response.PatientResponse;
import com.medcore.patient.domain.dto.response.PatientSummaryResponse;
import com.medcore.patient.domain.entity.Patient;
import com.medcore.patient.event.producer.PatientEventProducer;
import com.medcore.patient.exception.PatientException;
import com.medcore.patient.mapper.PatientMapper;
import com.medcore.patient.repository.PatientRepository;
import com.medcore.patient.repository.specification.PatientSpecification;
import com.medcore.patient.service.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PatientServiceImpl implements PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientServiceImpl.class);

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final PatientEventProducer eventProducer;

    public PatientServiceImpl(PatientRepository patientRepository,
                              PatientMapper patientMapper,
                              PatientEventProducer eventProducer) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
        this.eventProducer = eventProducer;
    }

    @Override
    @Transactional
    public PatientResponse crear(PatientRequest request, String requestId) {
        if (patientRepository.existsByDni(request.dni())) {
            throw PatientException.dniYaRegistrado(request.dni());
        }

        Patient paciente = new Patient(
                request.dni(), request.nombres(), request.apellidos(),
                request.fechaNacimiento(), request.sexo(), request.tipoSangre(),
                request.telefono(), normalizarCorreo(request.correo()),
                request.direccion(), request.zona());

        paciente.actualizarContactoEmergencia(
                request.contactoEmergenciaNombre(), request.contactoEmergenciaTelefono());
        paciente.registrarAlergias(request.alergias());

        Patient guardado = patientRepository.save(paciente);
        log.info("Paciente {} registrado con DNI {}", guardado.getId(), guardado.getDni());

        publicarTrasCommit(() -> eventProducer.publicarCreado(guardado, requestId));

        return patientMapper.aRespuesta(guardado);
    }

    @Override
    @Transactional
    public PatientResponse actualizar(Long id, PatientRequest request, String requestId) {
        Patient paciente = buscarEntidad(id);

        // El DNI del request se ignora a proposito: cambiarlo equivaldria a
        // sustituir a la persona de la ficha y dejaria su historial clinico
        // atribuido a otra.
        paciente.actualizarDatos(
                request.nombres(), request.apellidos(), request.fechaNacimiento(),
                request.sexo(), request.tipoSangre(), request.telefono(),
                normalizarCorreo(request.correo()), request.direccion(), request.zona());

        paciente.actualizarContactoEmergencia(
                request.contactoEmergenciaNombre(), request.contactoEmergenciaTelefono());
        paciente.registrarAlergias(request.alergias());

        Patient guardado = patientRepository.save(paciente);
        log.info("Paciente {} actualizado", id);

        publicarTrasCommit(() -> eventProducer.publicarActualizado(guardado, requestId));

        return patientMapper.aRespuesta(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse obtenerPorId(Long id) {
        return patientMapper.aRespuesta(buscarEntidad(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse obtenerPorDni(String dni) {
        return patientRepository.findByDni(dni)
                .map(patientMapper::aRespuesta)
                .orElseThrow(() -> PatientException.noEncontradoPorDni(dni));
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<PatientSummaryResponse> buscar(PatientSearchRequest criterios,
                                                           Pageable pageable) {
        if (criterios.edadMin() != null && criterios.edadMax() != null
                && criterios.edadMin() > criterios.edadMax()) {
            throw PatientException.rangoEdadInvalido();
        }

        Page<Patient> pagina = patientRepository.findAll(
                PatientSpecification.porCriterios(criterios), pageable);

        // Se devuelve el resumen y no la ficha completa: un listado no debe
        // transportar direcciones, alergias ni contactos de emergencia.
        return PageableResponse.de(pagina, patientMapper::aResumen);
    }

    @Override
    @Transactional
    public PatientResponse desactivar(Long id) {
        Patient paciente = buscarEntidad(id);
        paciente.desactivar();
        log.info("Ficha del paciente {} desactivada", id);
        return patientMapper.aRespuesta(patientRepository.save(paciente));
    }

    @Override
    @Transactional
    public PatientResponse reactivar(Long id) {
        Patient paciente = buscarEntidad(id);
        paciente.reactivar();
        log.info("Ficha del paciente {} reactivada", id);
        return patientMapper.aRespuesta(patientRepository.save(paciente));
    }

    @Override
    @Transactional
    public PatientResponse marcarFallecido(Long id) {
        Patient paciente = buscarEntidad(id);
        paciente.marcarFallecido();
        log.info("Ficha del paciente {} cerrada por fallecimiento", id);
        return patientMapper.aRespuesta(patientRepository.save(paciente));
    }

    private Patient buscarEntidad(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> PatientException.noEncontrado(id));
    }

    private String normalizarCorreo(String correo) {
        return (correo == null || correo.isBlank()) ? null : correo.toLowerCase().trim();
    }

    /**
     * Publica el evento solo despues de que la transaccion confirme.
     *
     * <p>Publicarlo dentro de la transaccion permitiria que otro servicio
     * reciba el patient.created y consulte un paciente que todavia no existe,
     * o peor, que la transaccion revierta despues y quede un evento de un
     * paciente que nunca llego a registrarse.
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
