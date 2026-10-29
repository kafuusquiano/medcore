package com.medcore.appointment.controller;

import com.medcore.appointment.domain.dto.request.AppointmentRequest;
import com.medcore.appointment.domain.dto.request.CancelRequest;
import com.medcore.appointment.domain.dto.response.AppointmentResponse;
import com.medcore.appointment.domain.dto.response.SlotResponse;
import com.medcore.appointment.service.AppointmentService;
import com.medcore.appointment.service.AvailabilityService;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Citas", description = "Reserva, consulta y ciclo de vida de las citas")
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AvailabilityService availabilityService;

    public AppointmentController(AppointmentService appointmentService,
                                 AvailabilityService availabilityService) {
        this.appointmentService = appointmentService;
        this.availabilityService = availabilityService;
    }

    @Operation(summary = "Consulta los horarios libres de un medico",
            description = """
                    Devuelve solo las franjas que siguen disponibles. Una
                    franja de hoy cuya hora ya paso no se ofrece, aunque en la
                    base figure libre.
                    """)
    @GetMapping("/slots")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> slotsDisponibles(
            @RequestParam Long idMedico,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        return ResponseEntity.ok(ApiResponse.ok("Horarios disponibles obtenidos.",
                availabilityService.slotsDisponibles(idMedico, fecha)));
    }

    @Operation(summary = "Reserva una cita",
            description = """
                    El horario se identifica por idSlot, elegido de la consulta
                    de disponibilidad. Si otra peticion reserva esa misma
                    franja a la vez, una de las dos recibe 409 HORARIO_OCUPADO.
                    """)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'PACIENTE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reservar(
            @Valid @RequestBody AppointmentRequest request,
            @RequestHeader(GatewayHeaders.USER_ID) String userId,
            @RequestHeader(GatewayHeaders.USER_ROLE) String rol,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        AppointmentResponse cita = appointmentService.reservar(request, userId, rol, requestId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cita reservada correctamente.", cita));
    }

    @Operation(summary = "Obtiene una cita")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cita encontrada.",
                appointmentService.obtener(id)));
    }

    @Operation(summary = "Citas de un paciente")
    @GetMapping("/paciente/{idPaciente}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<PageableResponse<AppointmentResponse>>> citasDePaciente(
            @PathVariable Long idPaciente,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Citas obtenidas.",
                appointmentService.citasDePaciente(idPaciente, pageable)));
    }

    @Operation(summary = "Agenda de un medico en una fecha")
    @GetMapping("/medico/{idMedico}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> agendaDeMedico(
            @PathVariable Long idMedico,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        return ResponseEntity.ok(ApiResponse.ok("Agenda obtenida.",
                appointmentService.agendaDeMedico(idMedico, fecha)));
    }

    @Operation(summary = "Confirma una cita")
    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'PACIENTE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cita confirmada.",
                appointmentService.confirmar(id)));
    }

    @Operation(summary = "Marca la cita como atendida",
            description = "Solo se permite sobre una cita confirmada.")
    @PatchMapping("/{id}/completar")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> completar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cita completada.",
                appointmentService.completar(id)));
    }

    @Operation(summary = "Marca que el paciente no acudio",
            description = "La franja no se libera: el horario se consumio igualmente.")
    @PatchMapping("/{id}/no-asistio")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> marcarNoAsistio(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cita marcada como no asistida.",
                appointmentService.marcarNoAsistio(id)));
    }

    @Operation(summary = "Cancela una cita",
            description = """
                    Exige la antelacion minima configurada. Al cancelar, las
                    franjas vuelven a quedar libres y se publica
                    appointment.cancelled.
                    """)
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'PACIENTE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Cita cancelada correctamente.",
                appointmentService.cancelar(id, request.motivo(), requestId)));
    }
}
