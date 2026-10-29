package com.medcore.appointment.service.impl;

import com.medcore.appointment.config.AppointmentProperties;
import com.medcore.appointment.domain.dto.response.SlotResponse;
import com.medcore.appointment.domain.entity.DoctorSchedule;
import com.medcore.appointment.domain.entity.TimeSlot;
import com.medcore.appointment.exception.AppointmentException;
import com.medcore.appointment.repository.DoctorScheduleRepository;
import com.medcore.appointment.repository.TimeSlotRepository;
import com.medcore.appointment.service.AvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityServiceImpl.class);

    private static final String CACHE_PREFIX = "medcore:agenda:slots:";

    private final TimeSlotRepository slotRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentProperties properties;
    private final StringRedisTemplate redis;

    public AvailabilityServiceImpl(TimeSlotRepository slotRepository,
                                   DoctorScheduleRepository scheduleRepository,
                                   AppointmentProperties properties,
                                   StringRedisTemplate redis) {
        this.slotRepository = slotRepository;
        this.scheduleRepository = scheduleRepository;
        this.properties = properties;
        this.redis = redis;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> slotsDisponibles(Long idMedico, LocalDate fecha) {
        List<TimeSlot> libres =
                slotRepository.findByIdMedicoAndFechaAndOcupadoFalseOrderByHoraInicio(idMedico, fecha);

        // Un slot de hoy que ya paso figura libre en la base pero no se puede
        // reservar, asi que no se ofrece.
        return libres.stream()
                .filter(s -> !s.yaPaso())
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> agendaDelDia(Long idMedico, LocalDate fecha) {
        return slotRepository.findByIdMedicoAndFechaOrderByHoraInicio(idMedico, fecha).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    @Transactional
    public int generarSlots(Long idMedico, LocalDate desde) {
        DoctorSchedule horario = scheduleRepository.findByIdMedicoAndActivoTrue(idMedico)
                .orElseThrow(() -> AppointmentException.horarioNoEncontrado(idMedico));

        LocalDate hasta = desde.plusDays(properties.horizonteDias());
        int creados = 0;

        for (LocalDate fecha = desde; fecha.isBefore(hasta); fecha = fecha.plusDays(1)) {
            if (!horario.atiendeEl(fecha.getDayOfWeek())) {
                continue;
            }
            // Si ya hay slots de esa fecha se omite el dia entero: generarlos
            // de nuevo chocaria con el indice unico.
            if (slotRepository.existsByIdMedicoAndFecha(idMedico, fecha)) {
                continue;
            }
            creados += generarDia(idMedico, fecha, horario);
        }

        if (creados > 0) {
            log.info("Generados {} slots para el medico {} desde {}", creados, idMedico, desde);
        }
        return creados;
    }

    private int generarDia(Long idMedico, LocalDate fecha, DoctorSchedule horario) {
        List<TimeSlot> nuevos = new ArrayList<>();
        LocalTime hora = horario.getHoraInicio();
        int duracion = properties.slotDuracionMinutos();

        while (!hora.plusMinutes(duracion).isAfter(horario.getHoraFin())) {
            nuevos.add(new TimeSlot(idMedico, fecha, hora, hora.plusMinutes(duracion)));
            hora = hora.plusMinutes(duracion);
        }

        try {
            slotRepository.saveAll(nuevos);
            return nuevos.size();
        } catch (DataIntegrityViolationException e) {
            // Otra instancia genero los mismos slots entre la comprobacion y
            // el guardado. El indice unico lo impidio, que es lo que se queria.
            log.debug("Los slots del medico {} para {} ya existian", idMedico, fecha);
            return 0;
        }
    }

    @Override
    public void invalidarCache(Long idMedico, LocalDate fecha) {
        try {
            redis.delete(claveCache(idMedico, fecha));
        } catch (RuntimeException e) {
            // Redis caido no debe impedir una reserva: la cache es una ayuda,
            // no la fuente de verdad. La disponibilidad se lee de la base.
            log.warn("No se pudo invalidar la cache de disponibilidad: {}", e.getMessage());
        }
    }

    private String claveCache(Long idMedico, LocalDate fecha) {
        return CACHE_PREFIX + idMedico + ":" + fecha;
    }

    private SlotResponse aRespuesta(TimeSlot slot) {
        return new SlotResponse(
                slot.getId(), slot.getIdMedico(), slot.getFecha(),
                slot.getHoraInicio(), slot.getHoraFin(),
                slot.estaLibre() && !slot.yaPaso());
    }
}
