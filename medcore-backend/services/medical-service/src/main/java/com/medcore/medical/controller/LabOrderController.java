package com.medcore.medical.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.medical.domain.dto.request.CancelRequest;
import com.medcore.medical.domain.dto.request.LabOrderRequest;
import com.medcore.medical.domain.dto.response.LabOrderResponse;
import com.medcore.medical.service.LabOrderService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ordenes de laboratorio.
 *
 * <p>Solo el medico las emite y las anula. El laboratorista puede
 * consultarlas, porque necesita saber que se solicito para procesar la
 * muestra que le llega.
 */
@Tag(name = "Ordenes de laboratorio", description = "Emision y seguimiento de ordenes")
@RestController
@RequestMapping("/api/lab-orders")
public class LabOrderController {

    private final LabOrderService labOrderService;

    public LabOrderController(LabOrderService labOrderService) {
        this.labOrderService = labOrderService;
    }

    @Operation(summary = "Emite una orden dentro de una atencion",
            description = """
                    Publica lab-order.created, que abre la orden en
                    laboratory-service y avisa al paciente. Devuelve el codigo
                    legible que el paciente lleva al laboratorio.
                    """)
    @PostMapping("/atencion/{idAtencion}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<LabOrderResponse>> emitir(
            @PathVariable Long idAtencion,
            @Valid @RequestBody LabOrderRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Orden de laboratorio emitida correctamente.",
                        labOrderService.emitir(idAtencion, request, requestId)));
    }

    @Operation(summary = "Obtiene una orden por su identificador")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<LabOrderResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Orden encontrada.",
                labOrderService.obtener(id)));
    }

    @Operation(summary = "Obtiene una orden por su codigo",
            description = "Es la busqueda del laboratorio cuando el paciente llega con la orden impresa.")
    @GetMapping("/codigo/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<LabOrderResponse>> obtenerPorCodigo(
            @PathVariable String codigo) {

        return ResponseEntity.ok(ApiResponse.ok("Orden encontrada.",
                labOrderService.obtenerPorCodigo(codigo)));
    }

    @Operation(summary = "Ordenes emitidas en una atencion")
    @GetMapping("/atencion/{idAtencion}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<LabOrderResponse>>> ordenesDeAtencion(
            @PathVariable Long idAtencion) {

        return ResponseEntity.ok(ApiResponse.ok("Ordenes obtenidas.",
                labOrderService.ordenesDeAtencion(idAtencion)));
    }

    @Operation(summary = "Ordenes de un paciente")
    @GetMapping("/paciente/{idPaciente}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<LabOrderResponse>>> ordenesDePaciente(
            @PathVariable Long idPaciente,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Ordenes obtenidas.",
                labOrderService.ordenesDePaciente(idPaciente, pageable)));
    }

    @Operation(summary = "Anula una orden",
            description = """
                    Solo mientras el laboratorio no haya tomado la muestra:
                    despues ya se pincho al paciente y hay material en proceso.
                    """)
    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<LabOrderResponse>> anular(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Orden anulada correctamente.",
                labOrderService.anular(id, request.motivo())));
    }
}
