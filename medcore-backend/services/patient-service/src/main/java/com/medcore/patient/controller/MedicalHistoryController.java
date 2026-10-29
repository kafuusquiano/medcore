package com.medcore.patient.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.patient.domain.dto.request.MedicalHistoryRequest;
import com.medcore.patient.domain.dto.response.MedicalHistoryResponse;
import com.medcore.patient.service.MedicalHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Linea de tiempo clinica del paciente.
 *
 * <p>Los episodios los registran otros servicios: medical-service al cerrar
 * una atencion y laboratory-service al emitir un resultado. Por eso el alta
 * esta restringida a MEDICO, LABORATORISTA y ADMIN, y nunca a recepcion.
 */
@Tag(name = "Historial clinico", description = "Episodios clinicos del paciente")
@RestController
@RequestMapping("/api/patients/{idPaciente}/history")
public class MedicalHistoryController {

    private final MedicalHistoryService historyService;

    public MedicalHistoryController(MedicalHistoryService historyService) {
        this.historyService = historyService;
    }

    @Operation(summary = "Consulta el historial de un paciente",
            description = "Devuelve los episodios del mas reciente al mas antiguo.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<MedicalHistoryResponse>>> historial(
            @PathVariable Long idPaciente,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Historial obtenido correctamente.",
                historyService.historialDe(idPaciente, pageable)));
    }

    @Operation(summary = "Registra un episodio",
            description = """
                    Es idempotente por la pareja (origen, referenciaExterna):
                    si el servicio de origen reintenta, se devuelve el episodio
                    ya registrado en lugar de crear otro.
                    """)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<MedicalHistoryResponse>> registrar(
            @PathVariable Long idPaciente,
            @Valid @RequestBody MedicalHistoryRequest request) {

        MedicalHistoryResponse episodio = historyService.registrarEpisodio(
                idPaciente,
                request.origen(),
                request.referenciaExterna(),
                request.titulo(),
                request.descripcion(),
                request.codigoCie10(),
                request.fechaEpisodio());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Episodio registrado correctamente.", episodio));
    }
}
