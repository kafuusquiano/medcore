package com.medcore.appointment.service;

import com.medcore.appointment.client.PatientClient;
import com.medcore.appointment.client.PatientDto;
import com.medcore.appointment.config.AppointmentProperties;
import com.medcore.appointment.domain.dto.request.AppointmentRequest;
import com.medcore.appointment.domain.entity.Appointment;
import com.medcore.appointment.domain.entity.DoctorSchedule;
import com.medcore.appointment.domain.entity.TimeSlot;
import com.medcore.appointment.domain.enums.AppointmentStatus;
import com.medcore.appointment.domain.enums.AppointmentType;
import com.medcore.appointment.event.producer.AppointmentEventProducer;
import com.medcore.appointment.exception.AppointmentException;
import com.medcore.appointment.mapper.AppointmentMapper;
import com.medcore.appointment.repository.AppointmentRepository;
import com.medcore.appointment.repository.DoctorScheduleRepository;
import com.medcore.appointment.repository.TimeSlotRepository;
import com.medcore.appointment.service.impl.AppointmentServiceImpl;
import com.medcore.commons.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceImplTest {

    private static final String USER_ID = "1";
    private static final String ROL = "RECEPCIONISTA";
    private static final String REQUEST_ID = "req-cita-1";

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private TimeSlotRepository slotRepository;
    @Mock
    private DoctorScheduleRepository scheduleRepository;
    @Mock
    private PatientClient patientClient;
    @Mock
    private AppointmentEventProducer eventProducer;
    @Mock
    private AvailabilityService availabilityService;

    private AppointmentServiceImpl service;

    private TimeSlot slot;
    private PatientDto paciente;

    @BeforeEach
    void preparar() {
        AppointmentProperties properties = new AppointmentProperties(30, 30, 24, 120);
        service = new AppointmentServiceImpl(appointmentRepository, slotRepository,
                scheduleRepository, patientClient, eventProducer, availabilityService,
                new AppointmentMapper(), properties);

        LocalDate manana = LocalDate.now().plusDays(3);
        slot = new TimeSlot(77L, manana, LocalTime.of(10, 0), LocalTime.of(10, 30));
        ReflectionTestUtils.setField(slot, "id", 5L);

        paciente = new PatientDto(2L, "12345678", "Ana Torres",
                "ana@correo.com", "999888777", "ACTIVO");

        DoctorSchedule horario = new DoctorSchedule(77L, "Dr. Vega", "Medicina General",
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(9, 0), LocalTime.of(12, 0));

        when(patientClient.obtenerPorId(anyLong(), anyString(), anyString(), any()))
                .thenReturn(ApiResponse.ok("ok", paciente));
        when(slotRepository.buscarParaReservar(5L)).thenReturn(Optional.of(slot));
        when(scheduleRepository.findByIdMedicoAndActivoTrue(77L)).thenReturn(Optional.of(horario));
        when(appointmentRepository.citasVivasDe(anyLong(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", 10L);
            }
            return a;
        });
    }

    private AppointmentRequest request(AppointmentType tipo) {
        return new AppointmentRequest(2L, 5L, tipo, "Control");
    }

    @Test
    @DisplayName("reservar_slotLibre_creaCitaYOcupaElSlot")
    void reservar_slotLibre_creaCitaYOcupaElSlot() {
        var respuesta = service.reservar(request(AppointmentType.CONSULTATION),
                USER_ID, ROL, REQUEST_ID);

        assertThat(respuesta.estado()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(slot.isOcupado()).isTrue();
        assertThat(slot.getIdCita()).isEqualTo(10L);
    }

    @Test
    @DisplayName("reservar_slotYaOcupado_lanzaHorarioOcupado")
    void reservar_slotYaOcupado_lanzaHorarioOcupado() {
        slot.reservar(99L);

        assertThatThrownBy(() -> service.reservar(request(AppointmentType.CONSULTATION),
                USER_ID, ROL, REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "HORARIO_OCUPADO");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("reservar_slotEnElPasado_lanzaHorarioPasado")
    void reservar_slotEnElPasado_lanzaHorarioPasado() {
        TimeSlot pasado = new TimeSlot(77L, LocalDate.now().minusDays(1),
                LocalTime.of(10, 0), LocalTime.of(10, 30));
        ReflectionTestUtils.setField(pasado, "id", 5L);
        when(slotRepository.buscarParaReservar(5L)).thenReturn(Optional.of(pasado));

        assertThatThrownBy(() -> service.reservar(request(AppointmentType.CONSULTATION),
                USER_ID, ROL, REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "HORARIO_PASADO");
    }

    @Test
    @DisplayName("reservar_pacienteNoActivo_lanzaPacienteNoActivo")
    void reservar_pacienteNoActivo_lanzaPacienteNoActivo() {
        when(patientClient.obtenerPorId(anyLong(), anyString(), anyString(), any()))
                .thenReturn(ApiResponse.ok("ok", new PatientDto(2L, "12345678",
                        "Ana Torres", null, null, "FALLECIDO")));

        assertThatThrownBy(() -> service.reservar(request(AppointmentType.CONSULTATION),
                USER_ID, ROL, REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "PACIENTE_NO_ACTIVO");

        // No se llega a tocar el slot si el paciente no admite atencion.
        verify(slotRepository, never()).buscarParaReservar(anyLong());
    }

    @Test
    @DisplayName("reservar_pacienteYaTieneCitaEseDia_lanzaCitaDuplicada")
    void reservar_pacienteYaTieneCitaEseDia_lanzaCitaDuplicada() {
        Appointment existente = new Appointment(2L, "12345678", "Ana Torres", null, null,
                77L, "Dr. Vega", 9L, slot.inicio().withHour(11),
                AppointmentType.CONSULTATION, "Otra");
        when(appointmentRepository.citasVivasDe(anyLong(), any())).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.reservar(request(AppointmentType.CONSULTATION),
                USER_ID, ROL, REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "CITA_DUPLICADA");
    }

    @Test
    @DisplayName("reservar_urgenciaSinSlotSiguiente_lanzaHorariosInsuficientes")
    void reservar_urgenciaSinSlotSiguiente_lanzaHorariosInsuficientes() {
        // Una urgencia necesita dos franjas seguidas y no hay siguiente.
        when(slotRepository.buscarDesde(anyLong(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.reservar(request(AppointmentType.EMERGENCY),
                USER_ID, ROL, REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "HORARIOS_INSUFICIENTES");
    }

    @Test
    @DisplayName("reservar_urgenciaConSlotSiguienteNoContiguo_lanzaHorariosInsuficientes")
    void reservar_urgenciaConSlotSiguienteNoContiguo_lanzaHorariosInsuficientes() {
        // Hay otra franja, pero empieza mas tarde: dejaria un hueco con otra
        // cita en medio de la urgencia.
        TimeSlot separado = new TimeSlot(77L, slot.getFecha(),
                LocalTime.of(11, 0), LocalTime.of(11, 30));
        ReflectionTestUtils.setField(separado, "id", 6L);
        when(slotRepository.buscarDesde(anyLong(), any(), any())).thenReturn(List.of(separado));

        assertThatThrownBy(() -> service.reservar(request(AppointmentType.EMERGENCY),
                USER_ID, ROL, REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "HORARIOS_INSUFICIENTES");
    }

    @Test
    @DisplayName("cancelar_sinAntelacionSuficiente_lanzaFueraDePlazo")
    void cancelar_sinAntelacionSuficiente_lanzaFueraDePlazo() {
        Appointment cita = new Appointment(2L, "12345678", "Ana Torres", null, null,
                77L, "Dr. Vega", 5L, LocalDateTime.now().plusHours(2),
                AppointmentType.CONSULTATION, "Control");
        ReflectionTestUtils.setField(cita, "id", 10L);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> service.cancelar(10L, "No puedo asistir", REQUEST_ID))
                .isInstanceOf(AppointmentException.class)
                .hasFieldOrPropertyWithValue("codigo", "CANCELACION_FUERA_DE_PLAZO");

        assertThat(cita.getEstado()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    @DisplayName("cancelar_conAntelacion_anulaYLiberaElSlot")
    void cancelar_conAntelacion_anulaYLiberaElSlot() {
        Appointment cita = new Appointment(2L, "12345678", "Ana Torres", null, null,
                77L, "Dr. Vega", 5L, LocalDateTime.now().plusDays(3),
                AppointmentType.CONSULTATION, "Control");
        ReflectionTestUtils.setField(cita, "id", 10L);
        slot.reservar(10L);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(cita));
        when(slotRepository.findByIdMedicoAndFechaOrderByHoraInicio(anyLong(), any()))
                .thenReturn(List.of(slot));

        var respuesta = service.cancelar(10L, "No puedo asistir", REQUEST_ID);

        assertThat(respuesta.estado()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(respuesta.motivoCancelacion()).isEqualTo("No puedo asistir");
        assertThat(slot.estaLibre()).isTrue();
    }

    @Test
    @DisplayName("marcarNoAsistio_noLiberaElSlot")
    void marcarNoAsistio_noLiberaElSlot() {
        Appointment cita = new Appointment(2L, "12345678", "Ana Torres", null, null,
                77L, "Dr. Vega", 5L, LocalDateTime.now().minusHours(3),
                AppointmentType.CONSULTATION, "Control");
        ReflectionTestUtils.setField(cita, "id", 10L);
        slot.reservar(10L);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(cita));

        service.marcarNoAsistio(10L);

        // La franja se consumio aunque el paciente no acudiera; liberarla
        // permitiria reservar un horario ya pasado.
        assertThat(slot.isOcupado()).isTrue();
    }

    @Test
    @DisplayName("transicionInvalida_completarUnaCitaSinConfirmar_lanzaIllegalState")
    void transicionInvalida_completarUnaCitaSinConfirmar_lanzaIllegalState() {
        Appointment cita = new Appointment(2L, "12345678", "Ana Torres", null, null,
                77L, "Dr. Vega", 5L, LocalDateTime.now().plusDays(1),
                AppointmentType.CONSULTATION, "Control");
        ReflectionTestUtils.setField(cita, "id", 10L);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> service.completar(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCHEDULED");
    }

    @Test
    @DisplayName("estadosFinales_noAdmitenMasTransiciones")
    void estadosFinales_noAdmitenMasTransiciones() {
        assertThat(AppointmentStatus.COMPLETED.siguientesValidos()).isEmpty();
        assertThat(AppointmentStatus.CANCELLED.siguientesValidos()).isEmpty();
        assertThat(AppointmentStatus.NO_SHOW.siguientesValidos()).isEmpty();
        assertThat(AppointmentStatus.SCHEDULED.puedePasarA(AppointmentStatus.COMPLETED)).isFalse();
        assertThat(AppointmentStatus.CONFIRMED.puedePasarA(AppointmentStatus.COMPLETED)).isTrue();
    }
}
