package com.medcore.appointment.scheduler;

import com.medcore.appointment.domain.entity.DoctorSchedule;
import com.medcore.appointment.repository.AppointmentRepository;
import com.medcore.appointment.repository.DoctorScheduleRepository;
import com.medcore.appointment.service.AvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tareas periodicas de la agenda.
 *
 * <p>Advertencia para cuando haya mas de una instancia del servicio: estas
 * tareas se ejecutarian en todas a la vez. La generacion de slots es
 * idempotente y el indice unico la protege, pero el cierre de citas vencidas
 * deberia coordinarse con ShedLock antes de escalar.
 */
@Component
public class SlotGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlotGenerationScheduler.class);

    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilityService availabilityService;

    public SlotGenerationScheduler(DoctorScheduleRepository scheduleRepository,
                                   AppointmentRepository appointmentRepository,
                                   AvailabilityService availabilityService) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.availabilityService = availabilityService;
    }

    /**
     * Extiende la agenda cada madrugada.
     *
     * <p>Sin esto el horizonte se iria agotando dia a dia y llegaria un
     * momento en que no habria franjas que ofrecer.
     */
    @Scheduled(cron = "${appointment.cron-generacion-slots:0 0 1 * * *}")
    public void generarSlotsDiarios() {
        List<DoctorSchedule> activos = scheduleRepository.findByActivoTrue();
        int total = 0;

        for (DoctorSchedule horario : activos) {
            try {
                total += availabilityService.generarSlots(horario.getIdMedico(), LocalDate.now());
            } catch (RuntimeException e) {
                // El fallo de un medico no debe impedir la generacion del resto.
                log.error("No se pudieron generar slots del medico {}: {}",
                        horario.getIdMedico(), e.getMessage());
            }
        }
        log.info("Generacion diaria completada: {} slots nuevos para {} medicos",
                total, activos.size());
    }

    /**
     * Marca como NO_SHOW las citas que pasaron y nadie cerro.
     *
     * <p>Se espera un margen de dos horas tras la hora de la cita: cerrarla
     * en el minuto exacto marcaria como ausente a un paciente que esta siendo
     * atendido en ese momento.
     */
    @Scheduled(cron = "${appointment.cron-cierre-vencidas:0 30 * * * *}")
    @Transactional
    public void cerrarCitasVencidas() {
        LocalDateTime limite = LocalDateTime.now().minusHours(2);
        var vencidas = appointmentRepository.vencidasSinCerrar(limite);

        vencidas.forEach(cita -> {
            try {
                cita.marcarNoAsistio();
                appointmentRepository.save(cita);
            } catch (IllegalStateException e) {
                log.warn("No se pudo cerrar la cita {}: {}", cita.getId(), e.getMessage());
            }
        });

        if (!vencidas.isEmpty()) {
            log.info("{} citas vencidas marcadas como NO_SHOW", vencidas.size());
        }
    }
}
