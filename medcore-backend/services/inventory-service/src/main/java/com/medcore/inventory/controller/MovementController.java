package com.medcore.inventory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.inventory.domain.dto.request.MovementRequest;
import com.medcore.inventory.domain.dto.response.MovementResponse;
import com.medcore.inventory.service.MovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Movimientos de stock.
 *
 * <p>Es el unico camino por el que cambia la cantidad de un lote. No hay
 * endpoint para editar una cantidad directamente, y eso es deliberado: toda
 * variacion deja su movimiento, de modo que el stock actual y el historial
 * nunca puedan discrepar.
 *
 * <p>Tampoco hay endpoint para borrar ni editar un movimiento. Un error se
 * corrige con un AJUSTE que deja constancia de la diferencia, porque es la
 * auditoria de un inventario de material sanitario.
 */
@Tag(name = "Movimientos", description = "Entradas, salidas, mermas y ajustes de stock")
@RestController
@RequestMapping("/api/movements")
public class MovementController {

    private final MovementService movementService;

    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    @Operation(summary = "Registra un movimiento sobre un lote",
            description = """
                    La cantidad se envia en positivo salvo en un AJUSTE: el
                    signo lo determina el tipo. MERMA, AJUSTE y DEVOLUCION
                    exigen motivo.
                    """)
    @PostMapping("/lote/{idStock}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<MovementResponse>> registrar(
            @PathVariable Long idStock,
            @Valid @RequestBody MovementRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Movimiento registrado correctamente.",
                        movementService.registrar(idStock, request, usuario, requestId)));
    }

    @Operation(summary = "Consume un reactivo repartiendo entre sus lotes",
            description = """
                    Gasta primero el lote que caduca antes y pasa al siguiente
                    si no alcanza. Es la operacion del laboratorio al procesar
                    una muestra: pide la cantidad sin elegir el lote. Si el
                    total no alcanza, no consume nada.
                    """)
    @PostMapping("/consumo/reactivo/{idReactivo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<MovementResponse>>> consumir(
            @PathVariable Long idReactivo,
            @Valid @RequestBody ConsumoRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Consumo registrado correctamente.",
                        movementService.consumir(idReactivo, request.cantidad(),
                                request.codigoMuestra(), usuario, requestId)));
    }

    @Operation(summary = "Historial de un lote")
    @GetMapping("/lote/{idStock}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<MovementResponse>>> historialDeLote(
            @PathVariable Long idStock,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Historial del lote.",
                PageableResponse.de(movementService.historialDeLote(idStock, pageable))));
    }

    @Operation(summary = "Historial de un reactivo, en todos sus lotes")
    @GetMapping("/reactivo/{codigoReactivo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<MovementResponse>>> historialDeReactivo(
            @PathVariable String codigoReactivo,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Historial del reactivo.",
                PageableResponse.de(
                        movementService.historialDeReactivo(codigoReactivo, pageable))));
    }

    @Operation(summary = "Historial completo del almacen")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<MovementResponse>>> historialCompleto(
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Historial de movimientos.",
                PageableResponse.de(movementService.historialCompleto(pageable))));
    }

    @Operation(summary = "Material que consumio una muestra",
            description = "Trazabilidad inversa: con que reactivos y lotes se proceso la muestra.")
    @GetMapping("/muestra/{codigoMuestra}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<MovementResponse>>> deMuestra(
            @PathVariable String codigoMuestra) {

        return ResponseEntity.ok(ApiResponse.ok("Material consumido por la muestra.",
                movementService.movimientosDeMuestra(codigoMuestra)));
    }

    @Operation(summary = "Consumo de un reactivo en los ultimos dias",
            description = "Sirve para estimar cuanto pedir en la siguiente reposicion.")
    @GetMapping("/consumo/{codigoReactivo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<Integer>> consumo(
            @PathVariable String codigoReactivo,
            @RequestParam(defaultValue = "30") int dias) {

        return ResponseEntity.ok(ApiResponse.ok(
                "Consumo de los ultimos " + dias + " dias.",
                movementService.consumoUltimosDias(codigoReactivo, dias)));
    }

    /**
     * Consumo de un reactivo sin elegir el lote.
     *
     * @param codigoMuestra muestra que consumio el material; queda en el
     *                      movimiento para la trazabilidad inversa
     */
    public record ConsumoRequest(
            @NotNull(message = "Indique la cantidad a consumir")
            @Min(value = 1, message = "La cantidad a consumir debe ser al menos 1")
            Integer cantidad,

            @Size(max = 25, message = "El codigo de muestra no puede pasar de 25 caracteres")
            String codigoMuestra
    ) {
    }
}
