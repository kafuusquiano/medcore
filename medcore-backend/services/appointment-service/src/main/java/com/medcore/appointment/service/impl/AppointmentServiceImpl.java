package com.medcore.appointment.service.impl;

import com.medcore.appointment.client.PatientClient;
import com.medcore.appointment.client.PatientDto;
import com.medcore.appointment.config.AppointmentProperties;
import com.medcore.appointment.domain.dto.request.AppointmentRequest;
import com.medcore.appointment.domain.dto.response.AppointmentResponse;
import com.medcore.appointment.domain.entity.Appointment;
import com.medcore.appointment.domain.entity.DoctorSchedule;
import com.medcore.appointment.domain.entity.TimeSlot;
import com.medcore.appointment.domain.enums.AppointmentType;
import com.medcore.appointment.event.producer.AppointmentEventProducer;
import com.medcore.appointment.exception.AppointmentException;
import com.medcore.appointment.mapper.AppointmentMapper;
import com.medcore.appointment.repository.AppointmentRepository;
import com.medcore.appointment.repository.DoctorScheduleRepository;
import com.medcore.appointment.repository.TimeSlotRepository;
import com.medcore.appointment.service.AppointmentService;
import com.medcore.appointment.service.AvailabilityService;
import com.medcore.commons.dto.PageableResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository slotRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PatientClient patientClient;
    private final AppointmentEventProducer eventProducer;
    private final AvailabilityService availabilityService;
    private final AppointmentMapper mapper;
    private final AppointmentProperties properties;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                  TimeSlotRepository slotRepository,
                                  DoctorScheduleRepository scheduleRepository,
                                  PatientClient patientClient,
                                  AppointmentEventProducer eventProducer,
                                  AvailabilityService availabilityService,
                                  AppointmentMapper mapper,
                                  AppointmentProperties properties) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.scheduleRepository = scheduleRepository;
        this.patientClient = patientClient;
        this.eventProducer = eventProducer;
        this.availabilityService = availabilityService;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * Reserva una cita.
     *
     * <p>Orden deliberado de los pasos:
     * <ol>
     *   <li>Se valida el paciente ANTES de tomar el bloqueo. Llamar a
     *       patient-service con el slot bloqueado mantendria el bloqueo
     *       abierto durante toda la llamada de red y bloquearia a los demas.</li>
     *   <li>Se lee el slot con bloqueo pesimista. Desde aqui, dos peticiones
     *       simultaneas sobre el mismo horario quedan serializadas.</li>
     *   <li>Se comprueba que siga libre. La segunda peticion llega aqui y ve
     *       el slot ya ocupado, de modo que responde un conflicto claro en
     *       lugar de un error de concurrencia.</li>
     * </ol>
     */
    @Override
    @Transactional
    public AppointmentResponse reservar(AppointmentRequest request, String userId,
                                        String rol, String requestId) {

        PatientDto paciente = verificarPaciente(request.idPaciente(), userId, rol, requestId);

        TimeSlot slot = slotRepository.buscarParaReservar(request.idSlot())
                .orElseThrow(() -> AppointmentException.slotNoEncontrado(request.idSlot()));

        if (slot.yaPaso()) {
            throw AppointmentException.slotEnElPasado();
        }
        if (!slot.estaLibre()) {
            throw AppointmentException.slotOcupado(slot.inicio());
        }

        impedirDuplicadaElMismoDia(request.idPaciente(), slot.inicio());

        DoctorSchedule horario = scheduleRepository
                .findByIdMedicoAndActivoTrue(slot.getIdMedico())
                .orElseThrow(() -> AppointmentException.horarioNoEncontrado(slot.getIdMedico()));

        // Una urgencia ocupa dos franjas seguidas; se reservan ambas o ninguna.
        List<TimeSlot> adicionales = reservarSlotsAdicionales(slot, request.tipo());

        Appointment cita = new Appointment(
                paciente.id(), paciente.dni(), paciente.nombreCompleto(),
                paciente.correo(), paciente.telefono(),
                slot.getIdMedico(), horario.getNombreMedico(), slot.getId(),
                slot.inicio(), request.tipo(), request.motivo());

        Appointment guardada = appointmentRepository.save(cita);

        slot.reservar(guardada.getId());
        slotRepository.save(slot);
        adicionales.forEach(s -> {
            s.reservar(guardada.getId());
            slotRepository.save(s);
        });

        log.info("Cita {} reservada para el paciente {} con el medico {} el {}",
                guardada.getId(), paciente.id(), slot.getIdMedico(), slot.inicio());

        trasCommit(() -> {
            eventProducer.publicarCreada(guardada, requestId);
            availabilityService.invalidarCache(slot.getIdMedico(), slot.getFecha());
        });

        return mapper.aRespuesta(guardada, properties.cancelacionAntelacionHoras());
    }

    private PatientDto verificarPaciente(Long idPaciente, String userId, String rol, String requestId) {
        // Si patient-service no responde, el fallback lanza
        // PACIENTE_NO_VERIFICABLE: no se reserva a ciegas.
        var respuesta = patientClient.obtenerPorId(idPaciente, userId, rol, requestId);

        PatientDto paciente = respuesta == null ? null : respuesta.data();
        if (paciente == null) {
            throw AppointmentException.pacienteNoVerificable();
        }
        if (!paciente.admiteAtencion()) {
            throw AppointmentException.pacienteNoAdmiteAtencion();
        }
        return paciente;
    }

    private void impedirDuplicadaElMismoDia(Long idPaciente, LocalDateTime fechaHora) {
        boolean yaTiene = appointmentRepository
                .citasVivasDe(idPaciente, LocalDateTime.now()).stream()
                .anyMatch(c -> c.getFechaHora().toLocalDate().equals(fechaHora.toLocalDate()));

        if (yaTiene) {
            throw AppointmentException.pacienteYaTieneCita(fechaHora);
        }
    }

    /**
     * Reserva las franjas extra que exige el tipo de cita.
     *
     * <p>Deben ser consecutivas: si entre ellas hay un hueco ocupado, la
     * urgencia se partiria en dos tramos separados y el medico tendria otra
     * cita en medio.
     */
    private List<TimeSlot> reservarSlotsAdicionales(TimeSlot primero, AppointmentType tipo) {
        int extra = tipo.slotsOcupados() - 1;
        if (extra <= 0) {
            return List.of();
        }

        List<TimeSlot> siguientes = slotRepository
                .buscarDesde(primero.getIdMedico(), primero.getFecha(), primero.getHoraFin());

        if (siguientes.size() < extra) {
            throw AppointmentException.slotsConsecutivosInsuficientes();
        }

        List<TimeSlot> elegidos = new java.util.ArrayList<>();
        var horaEsperada = primero.getHoraFin();

        for (int i = 0; i < extra; i++) {
            TimeSlot candidato = siguientes.get(i);
            if (!candidato.getHoraInicio().equals(horaEsperada) || !candidato.estaLibre()) {
                throw AppointmentException.slotsConsecutivosInsuficientes();
            }
            // Tambien con bloqueo: es un recurso en disputa igual que el primero.
            TimeSlot bloqueado = slotRepository.buscarParaReservar(candidato.getId())
                    .orElseThrow(() -> AppointmentException.slotNoEncontrado(candidato.getId()));
            if (!bloqueado.estaLibre()) {
                throw AppointmentException.slotOcupado(bloqueado.inicio());
            }
            elegidos.add(bloqueado);
            horaEsperada = candidato.getHoraFin();
        }
        return elegidos;
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse obtener(Long id) {
        return mapper.aRespuesta(buscarEntidad(id), properties.cancelacionAntelacionHoras());
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<AppointmentResponse> citasDePaciente(Long idPaciente, Pageable pageable) {
        var pagina = appointmentRepository.findByIdPacienteOrderByFechaHoraDesc(idPaciente, pageable);
        return PageableResponse.de(pagina,
                c -> mapper.aRespuesta(c, properties.cancelacionAntelacionHoras()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> agendaDeMedico(Long idMedico, LocalDate fecha) {
        return appointmentRepository
                .agendaDe(idMedico, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream()
                .map(c -> mapper.aRespuesta(c, properties.cancelacionAntelacionHoras()))
                .toList();
    }

    @Override
    @Transactional
    public AppointmentResponse confirmar(Long id) {
        Appointment cita = buscarEntidad(id);
        cita.confirmar();
        return mapper.aRespuesta(appointmentRepository.save(cita),
                properties.cancelacionAntelacionHoras());
    }

    @Override
    @Transactional
    public AppointmentResponse completar(Long id) {
        Appointment cita = buscarEntidad(id);
        cita.completar();
        return mapper.aRespuesta(appointmentRepository.save(cita),
                properties.cancelacionAntelacionHoras());
    }

    @Override
    @Transactional
    public AppointmentResponse marcarNoAsistio(Long id) {
        Appointment cita = buscarEntidad(id);
        cita.marcarNoAsistio();
        // El slot NO se libera: la franja se consumio aunque el paciente no
        // acudiera, y liberarla permitiria reservarla en un horario ya pasado.
        return mapper.aRespuesta(appointmentRepository.save(cita),
                properties.cancelacionAntelacionHoras());
    }

    @Override
    @Transactional
    public AppointmentResponse cancelar(Long id, String motivo, String requestId) {
        Appointment cita = buscarEntidad(id);

        if (cita.horasDeAntelacion() < properties.cancelacionAntelacionHoras()) {
            throw AppointmentException.cancelacionFueraDePlazo(
                    properties.cancelacionAntelacionHoras());
        }

        cita.cancelar(motivo);
        Appointment guardada = appointmentRepository.save(cita);

        // Al anular, las franjas vuelven a quedar libres para otro paciente.
        liberarSlotsDe(guardada);

        log.info("Cita {} cancelada: {}", id, motivo);

        trasCommit(() -> {
            eventProducer.publicarCancelada(guardada, requestId);
            availabilityService.invalidarCache(
                    guardada.getIdMedico(), guardada.getFechaHora().toLocalDate());
        });

        return mapper.aRespuesta(guardada, properties.cancelacionAntelacionHoras());
    }

    private void liberarSlotsDe(Appointment cita) {
        slotRepository.findByIdMedicoAndFechaOrderByHoraInicio(
                        cita.getIdMedico(), cita.getFechaHora().toLocalDate()).stream()
                .filter(s -> cita.getId().equals(s.getIdCita()))
                .forEach(s -> {
                    s.liberar();
                    slotRepository.save(s);
                });
    }

    private Appointment buscarEntidad(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> AppointmentException.citaNoEncontrada(id));
    }

    /**
     * Ejecuta la accion despues del commit.
     *
     * <p>Publicar el evento dentro de la transaccion permitiria que
     * notification-service avise al paciente de una cita que despues revierte.
     */
    private void trasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            accion.run();
                        }
                    });
        } else {
            accion.run();
        }
    }
}
