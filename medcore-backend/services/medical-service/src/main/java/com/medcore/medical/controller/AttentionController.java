package com.medcore.medical.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.request.AttentionRequest;
import com.medcore.medical.domain.dto.request.AttentionUpdateRequest;
import com.medcore.medical.domain.dto.request.CancelRequest;
import com.medcore.medical.domain.dto.request.DiagnosisRequest;
import com.medcore.medical.domain.dto.response.AttentionResponse;
import com.medcore.medical.service.AttentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Atenciones medicas.
 *
 * <p>Escribir en el registro clinico es tarea exclusiva del medico: ni
 * recepcion ni laboratorio pueden abrir, editar ni cerrar una atencion.
 */
@Tag(name = "Atenciones", description = "Consultas medicas y su registro clinico")
@RestController
@RequestMapping("/api/medical/attentions")
public class AttentionController {

    private final AttentionService attentionService;

    public AttentionController(AttentionService attentionService) {
        this.attentionService = attentionService;
    }

    @Operation(summary = "Abre una atencion",
            description = "idCita puede ir nulo si el paciente llega por urgencia, sin cita previa.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AttentionResponse>> abrir(
            @Valid @RequestBody AttentionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Atencion abierta correctamente.",
                        attentionService.abrir(request)));
    }

    @Operation(summary = "Registra hallazgos y signos vitales",
            description = "Solo se permite mientras la atencion siga abierta.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AttentionResponse>> registrarHallazgos(
            @PathVariable Long id,
            @Valid @RequestBody AttentionUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Hallazgos registrados correctamente.",
                attentionService.registrarHallazgos(id, request)));
    }

    @Operation(summary = "Agrega un diagnostico",
            description = """
                    La descripcion y la marca de notificable se toman del
                    catalogo CIE-10, no del cuerpo de la peticion. Como mucho
                    puede haber un diagnostico principal por atencion.
                    """)
    @PostMapping("/{id}/diagnosticos")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AttentionResponse>> agregarDiagnostico(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosisRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Diagnostico registrado correctamente.",
                        attentionService.agregarDiagnostico(id, request)));
    }

    @Operation(summary = "Finaliza la atencion",
            description = """
                    Exige al menos un diagnostico: una consulta sin diagnostico
                    no deja registro de por que se atendio al paciente ni sirve
                    para la estadistica epidemiologica. Una vez finalizada no
                    admite cambios.
                    """)
    @PatchMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AttentionResponse>> finalizar(
            @PathVariable Long id,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false)
            String requestId) {
        return ResponseEntity.ok(ApiResponse.ok("Atencion finalizada correctamente.",
                attentionService.finalizar(id, requestId)));
    }

    @Operation(summary = "Anula la atencion",
            description = "Para corregir un error de registro. Se conserva para auditoria.")
    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AttentionResponse>> anular(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Atencion anulada correctamente.",
                attentionService.anular(id, request.motivo())));
    }

    @Operation(summary = "Obtiene una atencion con sus diagnosticos")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<AttentionResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Atencion encontrada.",
                attentionService.obtener(id)));
    }

    @Operation(summary = "Atenciones de un paciente")
    @GetMapping("/paciente/{idPaciente}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<AttentionResponse>>> atencionesDePaciente(
            @PathVariable Long idPaciente,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Atenciones obtenidas.",
                attentionService.atencionesDePaciente(idPaciente, pageable)));
    }

    @Operation(summary = "Atenciones de un medico")
    @GetMapping("/medico/{idMedico}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<PageableResponse<AttentionResponse>>> atencionesDeMedico(
            @PathVariable Long idMedico,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Atenciones obtenidas.",
                attentionService.atencionesDeMedico(idMedico, pageable)));
    }
}
