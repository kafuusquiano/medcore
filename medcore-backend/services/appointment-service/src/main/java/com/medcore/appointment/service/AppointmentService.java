package com.medcore.appointment.service;

import com.medcore.appointment.domain.dto.request.AppointmentRequest;
import com.medcore.appointment.domain.dto.response.AppointmentResponse;
import com.medcore.commons.dto.PageableResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Citas.
 *
 * <p>Las operaciones de escritura reciben la identidad del solicitante porque
 * la llamada a patient-service necesita reenviar las cabeceras del gateway, y
 * el requestId para que la trazabilidad llegue hasta el evento Kafka.
 */
public interface AppointmentService {

    AppointmentResponse reservar(AppointmentRequest request, String userId, String rol, String requestId);

    AppointmentResponse obtener(Long id);

    PageableResponse<AppointmentResponse> citasDePaciente(Long idPaciente, Pageable pageable);

    List<AppointmentResponse> agendaDeMedico(Long idMedico, LocalDate fecha);

    AppointmentResponse confirmar(Long id);

    AppointmentResponse completar(Long id);

    AppointmentResponse marcarNoAsistio(Long id);

    AppointmentResponse cancelar(Long id, String motivo, String requestId);
}
