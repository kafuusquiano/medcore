package com.medcore.inventory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.inventory.domain.dto.request.ReagentRequest;
import com.medcore.inventory.domain.dto.response.ReagentResponse;
import com.medcore.inventory.service.ReagentService;
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

import java.util.List;

/**
 * Catalogo de reactivos e insumos.
 *
 * <p>Lo mantiene el laboratorista, que es quien conoce el material. El medico
 * no entra aqui: no pide reactivos, pide pruebas.
 */
@Tag(name = "Reactivos", description = "Catalogo de reactivos e insumos")
@RestController
@RequestMapping("/api/reagents")
public class ReagentController {

    private final ReagentService reagentService;

    public ReagentController(ReagentService reagentService) {
        this.reagentService = reagentService;
    }

    @Operation(summary = "Da de alta un reactivo",
            description = """
                    No lleva cantidades: el material fisico entra como lote,
                    con su fecha de vencimiento, por la API de stock.
                    """)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ReagentResponse>> crear(
            @Valid @RequestBody ReagentRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Reactivo creado correctamente.",
                        reagentService.crear(request, requestId)));
    }

    @Operation(summary = "Actualiza los datos de un reactivo",
            description = "El codigo no es modificable: esta rotulado en la estanteria.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ReagentResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ReagentRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Reactivo actualizado correctamente.",
                reagentService.actualizar(id, request, requestId)));
    }

    @Operation(summary = "Da de baja un reactivo",
            description = """
                    No lo borra: sus movimientos historicos tienen que seguir
                    siendo legibles. La baja impide registrar material nuevo,
                    no consultar lo que ya hay.
                    """)
    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ReagentResponse>> desactivar(
            @PathVariable Long id,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Reactivo dado de baja.",
                reagentService.desactivar(id, requestId)));
    }

    @Operation(summary = "Reactiva un reactivo dado de baja")
    @PatchMapping("/{id}/alta")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ReagentResponse>> reactivar(
            @PathVariable Long id,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Reactivo reactivado.",
                reagentService.reactivar(id, requestId)));
    }

    @Operation(summary = "Ficha de un reactivo con sus lotes",
            description = "Incluye el total disponible y la situacion de cada lote.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ReagentResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Reactivo encontrado.",
                reagentService.obtenerPorId(id)));
    }

    @Operation(summary = "Busca un reactivo por su codigo de estanteria")
    @GetMapping("/codigo/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ReagentResponse>> obtenerPorCodigo(
            @PathVariable String codigo) {

        return ResponseEntity.ok(ApiResponse.ok("Reactivo encontrado.",
                reagentService.obtenerPorCodigo(codigo)));
    }

    @Operation(summary = "Lista los reactivos activos")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<ReagentResponse>>> listar(
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Reactivos activos.",
                PageableResponse.de(reagentService.listarActivos(pageable))));
    }

    @Operation(summary = "Reactivos de una categoria")
    @GetMapping("/categoria/{categoria}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<ReagentResponse>>> porCategoria(
            @PathVariable String categoria) {

        return ResponseEntity.ok(ApiResponse.ok("Reactivos de la categoria " + categoria + ".",
                reagentService.listarPorCategoria(categoria)));
    }

    @Operation(summary = "Busca reactivos por nombre")
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<ReagentResponse>>> buscar(@RequestParam String texto) {
        return ResponseEntity.ok(ApiResponse.ok("Resultado de la busqueda.",
                reagentService.buscar(texto)));
    }

    @Operation(summary = "Categorias con reactivos activos",
            description = "Para agrupar el catalogo en la interfaz.")
    @GetMapping("/categorias")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<String>>> categorias() {
        return ResponseEntity.ok(ApiResponse.ok("Categorias del almacen.",
                reagentService.categorias()));
    }

    @Operation(summary = "Reactivos bajo minimo",
            description = """
                    La lista para hacer el pedido, con la cantidad sugerida ya
                    calculada. Suma solo lotes vigentes: un almacen lleno de
                    material caducado no es stock.
                    """)
    @GetMapping("/bajo-minimo")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<ReagentResponse>>> bajoMinimo() {
        return ResponseEntity.ok(ApiResponse.ok("Reactivos por reponer.",
                reagentService.bajoMinimo()));
    }
}
