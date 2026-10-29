package com.medcore.patient.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.patient.domain.dto.request.PatientRequest;
import com.medcore.patient.domain.dto.request.PatientSearchRequest;
import com.medcore.patient.domain.dto.response.PatientResponse;
import com.medcore.patient.domain.dto.response.PatientSummaryResponse;
import com.medcore.patient.domain.enums.Gender;
import com.medcore.patient.domain.enums.PatientStatus;
import com.medcore.patient.service.PatientService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fichas de pacientes.
 *
 * <p>Los roles se comprueban con {@code @PreAuthorize} sobre la autoridad que
 * construye el HeaderAuthenticationFilter a partir de {@code X-User-Role}.
 * Registrar y editar es tarea de recepcion y administracion; consultar lo
 * necesita ademas el personal clinico.
 */
@Tag(name = "Pacientes", description = "Alta, consulta y busqueda de fichas de pacientes")
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @Operation(summary = "Registra un paciente",
            description = "Publica el evento patient.created, que crea la cuenta de acceso del paciente.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<ApiResponse<PatientResponse>> crear(
            @Valid @RequestBody PatientRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        PatientResponse creado = patientService.crear(request, requestId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Paciente registrado correctamente.", creado));
    }

    @Operation(summary = "Actualiza una ficha",
            description = "El DNI no se modifica: cambiarlo dejaria el historial clinico atribuido a otra persona.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<ApiResponse<PatientResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PatientRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Paciente actualizado correctamente.",
                patientService.actualizar(id, request, requestId)));
    }

    @Operation(summary = "Obtiene una ficha por su identificador")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PatientResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Paciente encontrado.",
                patientService.obtenerPorId(id)));
    }

    @Operation(summary = "Obtiene una ficha por DNI",
            description = "Es la busqueda de recepcion cuando el paciente llega con su documento.")
    @GetMapping("/dni/{dni}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PatientResponse>> obtenerPorDni(@PathVariable String dni) {
        return ResponseEntity.ok(ApiResponse.ok("Paciente encontrado.",
                patientService.obtenerPorDni(dni)));
    }

    @Operation(summary = "Busca pacientes",
            description = """
                    Todos los filtros son opcionales y se combinan. El parametro
                    texto busca a la vez en DNI, nombres y apellidos. Devuelve
                    una vista reducida: un listado no transporta direcciones,
                    alergias ni contactos de emergencia.
                    """)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<PatientSummaryResponse>>> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Gender sexo,
            @RequestParam(required = false) PatientStatus estado,
            @RequestParam(required = false) String zona,
            @RequestParam(required = false) Integer edadMin,
            @RequestParam(required = false) Integer edadMax,
            @PageableDefault(size = 20, sort = "apellidos") Pageable pageable) {

        var criterios = new PatientSearchRequest(texto, sexo, estado, zona, edadMin, edadMax);
        return ResponseEntity.ok(ApiResponse.ok("Busqueda completada.",
                patientService.buscar(criterios, pageable)));
    }

    @Operation(summary = "Desactiva una ficha",
            description = "La ficha deja de admitir citas nuevas. El historial se conserva.")
    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<ApiResponse<PatientResponse>> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Ficha desactivada correctamente.",
                patientService.desactivar(id)));
    }

    @Operation(summary = "Reactiva una ficha",
            description = "No se permite sobre la ficha de un paciente fallecido.")
    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<ApiResponse<PatientResponse>> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Ficha reactivada correctamente.",
                patientService.reactivar(id)));
    }

    @Operation(summary = "Cierra la ficha por fallecimiento",
            description = "Operacion irreversible: la ficha no vuelve a admitir atenciones.")
    @PatchMapping("/{id}/fallecido")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<PatientResponse>> marcarFallecido(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Ficha cerrada por fallecimiento.",
                patientService.marcarFallecido(id)));
    }
}
