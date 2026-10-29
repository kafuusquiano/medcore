package com.medcore.appointment.controller;

import com.medcore.appointment.domain.dto.request.ScheduleRequest;
import com.medcore.appointment.domain.dto.response.ScheduleResponse;
import com.medcore.appointment.service.ScheduleService;
import com.medcore.commons.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Horarios de atencion.
 *
 * <p>Configurar un horario es tarea administrativa, no clinica: define la
 * disponibilidad de un medico para toda la clinica.
 */
@Tag(name = "Horarios", description = "Horarios de atencion de los medicos")
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "Configura el horario de un medico",
            description = "Genera de inmediato los slots del horizonte configurado.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> crear(
            @Valid @RequestBody ScheduleRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Horario configurado correctamente.",
                        scheduleService.crear(request)));
    }

    @Operation(summary = "Actualiza un horario",
            description = "Los slots ya generados se conservan: pueden tener citas reservadas.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Horario actualizado correctamente.",
                scheduleService.actualizar(id, request)));
    }

    @Operation(summary = "Horario vigente de un medico")
    @GetMapping("/medico/{idMedico}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> obtenerPorMedico(
            @PathVariable Long idMedico) {

        return ResponseEntity.ok(ApiResponse.ok("Horario encontrado.",
                scheduleService.obtenerPorMedico(idMedico)));
    }

    @Operation(summary = "Lista los horarios activos",
            description = "Permite filtrar por especialidad para elegir medico al reservar.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> listar(
            @RequestParam(required = false) String especialidad) {

        return ResponseEntity.ok(ApiResponse.ok("Horarios obtenidos.",
                scheduleService.listar(especialidad)));
    }

    @Operation(summary = "Desactiva un horario",
            description = "El medico deja de ofrecerse. Las citas ya reservadas se mantienen.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        scheduleService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Horario desactivado correctamente."));
    }
}
