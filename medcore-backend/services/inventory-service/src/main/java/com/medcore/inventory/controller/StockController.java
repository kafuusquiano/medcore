package com.medcore.inventory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.inventory.domain.dto.request.StockEntryRequest;
import com.medcore.inventory.domain.dto.response.StockResponse;
import com.medcore.inventory.service.AlertService;
import com.medcore.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * Lotes en almacen y avisos.
 *
 * <p>La cantidad de un lote no se edita por aqui: cambia solo registrando un
 * movimiento, en MovementController. Es lo que garantiza que el stock actual
 * y el historial nunca discrepen.
 */
@Tag(name = "Stock", description = "Lotes en almacen, vencimientos y avisos")
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;
    private final AlertService alertService;

    public StockController(StockService stockService, AlertService alertService) {
        this.stockService = stockService;
        this.alertService = alertService;
    }

    @Operation(summary = "Registra la entrada de un lote",
            description = """
                    Deja tambien su movimiento de ENTRADA: la cantidad de un
                    lote nunca cambia sin un movimiento que lo justifique, ni
                    siquiera al crearlo.
                    """)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<StockResponse>> ingresar(
            @Valid @RequestBody StockEntryRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Lote ingresado correctamente.",
                        stockService.ingresar(request, usuario, requestId)));
    }

    @Operation(summary = "Retira un lote del almacen",
            description = """
                    No pone la cantidad a cero: para eso se registra una MERMA,
                    que deja constancia de cuantas unidades se perdieron.
                    """)
    @PatchMapping("/{id}/retiro")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<StockResponse>> retirar(
            @PathVariable Long id,
            @Valid @RequestBody MotivoRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Lote retirado.",
                stockService.retirar(id, request.motivo(), requestId)));
    }

    @Operation(summary = "Cambia la ubicacion de un lote")
    @PatchMapping("/{id}/ubicacion")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<StockResponse>> reubicar(
            @PathVariable Long id,
            @Valid @RequestBody UbicacionRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Lote reubicado.",
                stockService.reubicar(id, request.ubicacion(), requestId)));
    }

    @Operation(summary = "Obtiene un lote")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<StockResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lote encontrado.",
                stockService.obtenerPorId(id)));
    }

    @Operation(summary = "Lotes vigentes en almacen")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<StockResponse>>> listar(
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Lotes en almacen.",
                PageableResponse.de(stockService.listarVigentes(pageable))));
    }

    @Operation(summary = "Lotes de un reactivo")
    @GetMapping("/reactivo/{idReactivo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> lotesDeReactivo(
            @PathVariable Long idReactivo) {

        return ResponseEntity.ok(ApiResponse.ok("Lotes del reactivo.",
                stockService.lotesDeReactivo(idReactivo)));
    }

    @Operation(summary = "Lotes de los que conviene consumir",
            description = """
                    En orden de vencimiento: el que caduca antes va primero, y
                    los que no caducan al final.
                    """)
    @GetMapping("/reactivo/{idReactivo}/consumibles")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> consumibles(
            @PathVariable Long idReactivo) {

        return ResponseEntity.ok(ApiResponse.ok("Lotes consumibles.",
                stockService.lotesConsumibles(idReactivo)));
    }

    @Operation(summary = "Lotes vencidos que siguen en la estanteria",
            description = "Hay que retirarlos antes de que alguien los use.")
    @GetMapping("/vencidos")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> vencidos() {
        return ResponseEntity.ok(ApiResponse.ok("Lotes vencidos.",
                stockService.vencidos()));
    }

    @Operation(summary = "Lotes que vencen dentro del plazo de aviso",
            description = "El plazo se configura en inventory-service.yml; por omision 30 dias.")
    @GetMapping("/por-vencer")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> porVencer() {
        return ResponseEntity.ok(ApiResponse.ok("Lotes por vencer.",
                stockService.porVencer()));
    }

    @Operation(summary = "Lanza la revision del almacen a demanda",
            description = """
                    El mismo repaso que hace el proceso programado cada
                    madrugada: publica stock.low por cada reactivo bajo minimo
                    y stock.expired por cada lote vencido o por vencer. Util
                    para no esperar al dia siguiente despues de un recuento.
                    """)
    @PostMapping("/revision")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<AlertService.Resumen>> revisar(
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Revision ejecutada.",
                alertService.revisar(requestId)));
    }

    /**
     * Motivo del retiro.
     *
     * <p>Va en el cuerpo y no como parametro de consulta: puede llevar tildes
     * y espacios, y queda registrado en el lote.
     */
    public record MotivoRequest(
            @NotBlank(message = "Indique por que se retira el lote")
            @Size(max = 300, message = "El motivo no puede pasar de 300 caracteres")
            String motivo
    ) {
    }

    public record UbicacionRequest(
            @NotBlank(message = "Indique la nueva ubicacion")
            @Size(max = 80, message = "La ubicacion no puede pasar de 80 caracteres")
            String ubicacion
    ) {
    }
}
