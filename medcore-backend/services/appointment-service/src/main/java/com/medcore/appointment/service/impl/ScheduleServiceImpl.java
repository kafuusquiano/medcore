package com.medcore.appointment.service.impl;

import com.medcore.appointment.domain.dto.request.ScheduleRequest;
import com.medcore.appointment.domain.dto.response.ScheduleResponse;
import com.medcore.appointment.domain.entity.DoctorSchedule;
import com.medcore.appointment.exception.AppointmentException;
import com.medcore.appointment.mapper.ScheduleMapper;
import com.medcore.appointment.repository.DoctorScheduleRepository;
import com.medcore.appointment.service.AvailabilityService;
import com.medcore.appointment.service.ScheduleService;
import com.medcore.commons.exception.MedCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleServiceImpl.class);

    private final DoctorScheduleRepository scheduleRepository;
    private final AvailabilityService availabilityService;
    private final ScheduleMapper mapper;

    public ScheduleServiceImpl(DoctorScheduleRepository scheduleRepository,
                               AvailabilityService availabilityService,
                               ScheduleMapper mapper) {
        this.scheduleRepository = scheduleRepository;
        this.availabilityService = availabilityService;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ScheduleResponse crear(ScheduleRequest request) {
        validarRango(request);

        if (scheduleRepository.existsByIdMedicoAndActivoTrue(request.idMedico())) {
            throw AppointmentException.horarioYaConfigurado(request.idMedico());
        }

        DoctorSchedule horario = new DoctorSchedule(
                request.idMedico(), request.nombreMedico(), request.especialidad(),
                request.dias(), request.horaInicio(), request.horaFin());

        DoctorSchedule guardado = scheduleRepository.save(horario);
        log.info("Horario {} creado para el medico {}", guardado.getId(), request.idMedico());

        // Se generan los slots de inmediato: si se esperara a la tarea
        // nocturna, el medico quedaria sin agenda hasta el dia siguiente.
        int creados = availabilityService.generarSlots(request.idMedico(), LocalDate.now());
        log.info("Generados {} slots iniciales para el medico {}", creados, request.idMedico());

        return mapper.aRespuesta(guardado);
    }

    @Override
    @Transactional
    public ScheduleResponse actualizar(Long id, ScheduleRequest request) {
        validarRango(request);

        DoctorSchedule horario = scheduleRepository.findById(id)
                .orElseThrow(() -> AppointmentException.horarioNoEncontrado(request.idMedico()));

        horario.actualizar(request.nombreMedico(), request.especialidad(),
                request.dias(), request.horaInicio(), request.horaFin());

        DoctorSchedule guardado = scheduleRepository.save(horario);
        log.info("Horario {} actualizado", id);

        // Los slots ya generados con el horario anterior se conservan: pueden
        // tener citas reservadas. Los nuevos dias se cubren en la siguiente
        // generacion, que omite las fechas que ya tienen franjas.
        availabilityService.generarSlots(horario.getIdMedico(), LocalDate.now());

        return mapper.aRespuesta(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse obtenerPorMedico(Long idMedico) {
        return scheduleRepository.findByIdMedicoAndActivoTrue(idMedico)
                .map(mapper::aRespuesta)
                .orElseThrow(() -> AppointmentException.horarioNoEncontrado(idMedico));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> listar(String especialidad) {
        var horarios = (especialidad == null || especialidad.isBlank())
                ? scheduleRepository.findByActivoTrue()
                : scheduleRepository.findByEspecialidadIgnoreCaseAndActivoTrue(especialidad);

        return horarios.stream().map(mapper::aRespuesta).toList();
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        DoctorSchedule horario = scheduleRepository.findById(id)
                .orElseThrow(() -> new MedCoreException("HORARIO_NO_ENCONTRADO",
                        HttpStatus.NOT_FOUND, "No existe el horario indicado."));

        horario.desactivar();
        scheduleRepository.save(horario);
        log.info("Horario {} desactivado", id);
    }

    /**
     * El rango horario cruza dos campos, asi que no se puede validar con una
     * anotacion sobre uno solo.
     */
    private void validarRango(ScheduleRequest request) {
        if (!request.rangoHorarioValido()) {
            throw new MedCoreException("RANGO_HORARIO_INVALIDO", HttpStatus.BAD_REQUEST,
                    "La hora de fin debe ser posterior a la de inicio.");
        }
    }
}
