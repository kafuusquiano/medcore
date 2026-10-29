package com.medcore.medical.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.response.Cie10Response;
import com.medcore.medical.domain.dto.response.DiagnosisResponse;
import com.medcore.medical.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Catalogo CIE-10 y consultas sobre los diagnosticos emitidos.
 */
@Tag(name = "Diagnosticos", description = "Catalogo CIE-10 y estadistica de diagnosticos")
@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @Operation(summary = "Busca en el catalogo CIE-10",
            description = "Busca por codigo o por texto de la descripcion.")
    @GetMapping("/cie10")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<Cie10Response>>> buscarCie10(
            @RequestParam(required = false) String texto,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Busqueda completada.",
                diagnosisService.buscarCie10(texto, pageable)));
    }

    @Operation(summary = "Obtiene un codigo CIE-10")
    @GetMapping("/cie10/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<Cie10Response>> obtenerCie10(@PathVariable String codigo) {
        return ResponseEntity.ok(ApiResponse.ok("Codigo encontrado.",
                diagnosisService.obtenerCie10(codigo)));
    }

    @Operation(summary = "Casos de notificacion obligatoria",
            description = "Diagnosticos notificables a la DIRESA en un intervalo de fechas.")
    @GetMapping("/notificables")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<DiagnosisResponse>>> notificables(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(ApiResponse.ok("Casos notificables obtenidos.",
                diagnosisService.notificablesEntre(desde, hasta)));
    }

    @Operation(summary = "Conteo de diagnosticos por codigo",
            description = "Base de los indicadores epidemiologicos, ordenado de mayor a menor.")
    @GetMapping("/conteo")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<DiagnosisService.ConteoDiagnostico>>> conteo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(ApiResponse.ok("Conteo obtenido.",
                diagnosisService.conteoPorCodigo(desde, hasta)));
    }
}
