package com.medcore.laboratory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.laboratory.domain.dto.response.PendingOrderResponse;
import com.medcore.laboratory.service.LabOrderQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cola de ordenes que el laboratorio espera recibir.
 *
 * <p>Es la pantalla de recepcion: el tecnico busca por documento al paciente
 * que tiene delante y ve que pruebas hay que hacerle. Solo lectura, porque la
 * cola la llena el consumidor de {@code lab-order.created}.
 */
@Tag(name = "Cola de ordenes", description = "Ordenes pendientes de muestra")
@RestController
@RequestMapping("/api/lab-queue")
public class LabOrderQueueController {

    private final LabOrderQueueService colaService;

    public LabOrderQueueController(LabOrderQueueService colaService) {
        this.colaService = colaService;
    }

    @Operation(summary = "Ordenes en espera de muestra",
            description = """
                    Urgentes primero y, dentro de cada grupo, de la mas
                    antigua a la mas reciente. Cada orden indica cuantas horas
                    lleva esperando, para detectar las que el paciente nunca
                    atendio.
                    """)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<PendingOrderResponse>>> pendientes(
            @RequestParam(defaultValue = "50") int limite) {

        return ResponseEntity.ok(ApiResponse.ok("Ordenes pendientes.",
                colaService.pendientes(limite)));
    }

    @Operation(summary = "Ordenes pendientes de un paciente por su documento",
            description = "Es la busqueda que hace el tecnico con el paciente en el mostrador.")
    @GetMapping("/dni/{dni}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<PendingOrderResponse>>> porDni(
            @PathVariable String dni) {

        return ResponseEntity.ok(ApiResponse.ok("Ordenes pendientes del paciente.",
                colaService.pendientesDeDni(dni)));
    }

    @Operation(summary = "Obtiene una orden de la cola")
    @GetMapping("/{idOrden}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PendingOrderResponse>> obtener(@PathVariable Long idOrden) {
        return ResponseEntity.ok(ApiResponse.ok("Orden encontrada.",
                colaService.obtenerPorId(idOrden)));
    }

    @Operation(summary = "Cuantas ordenes esperan y cuantas son urgentes",
            description = "Para el panel del laboratorio.")
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<LabOrderQueueService.Resumen>> resumen() {
        return ResponseEntity.ok(ApiResponse.ok("Resumen de la cola.",
                colaService.resumen()));
    }
}
