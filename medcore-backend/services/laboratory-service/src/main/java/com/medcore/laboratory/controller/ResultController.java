package com.medcore.laboratory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.laboratory.domain.dto.request.ResultEntryRequest;
import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Resultados de laboratorio.
 *
 * <p>Los registra y corrige el laboratorista. El medico los consulta, incluido
 * el historial de una prueba, que es lo que le permite ver si un valor sube o
 * baja entre controles.
 */
@Tag(name = "Resultados", description = "Registro, correccion y consulta de resultados")
@RestController
@RequestMapping("/api/results")
public class ResultController {

    private final ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @Operation(summary = "Registra un resultado a mano",
            description = """
                    Para pruebas que no emite un equipo conectado: una lectura
                    al microscopio, un cultivo que se informa en texto, o un
                    analizador fuera de linea. El valor se interpreta contra
                    los rangos del catalogo segun el sexo y la edad del
                    paciente, y si resulta critico se publica result.critical.
                    """)
    @PostMapping("/muestra/{idMuestra}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ResultResponse>> registrar(
            @PathVariable Long idMuestra,
            @Valid @RequestBody ResultEntryRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Resultado registrado correctamente.",
                        resultService.registrarManual(idMuestra, request, usuario, requestId)));
    }

    @Operation(summary = "Corrige un resultado ya emitido",
            description = """
                    No modifica el anterior: anade otro que lo rectifica y lo
                    apunta. Un resultado informado es un registro clinico y el
                    historial debe conservar lo que se dijo en su momento.
                    """)
    @PostMapping("/{id}/correccion")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ResultResponse>> corregir(
            @PathVariable Long id,
            @Valid @RequestBody ResultEntryRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Correccion registrada correctamente.",
                        resultService.corregir(id, request, usuario, requestId)));
    }

    @Operation(summary = "Resultados de una muestra")
    @GetMapping("/muestra/{idMuestra}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<ResultResponse>>> deMuestra(
            @PathVariable Long idMuestra) {

        return ResponseEntity.ok(ApiResponse.ok("Resultados de la muestra.",
                resultService.listarDeMuestra(idMuestra)));
    }

    @Operation(summary = "Obtiene un resultado por su identificador")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<ResultResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Resultado encontrado.",
                resultService.obtenerPorId(id)));
    }

    @Operation(summary = "Valores criticos del dia",
            description = """
                    Los que obligan a avisar al medico de inmediato. El
                    laboratorio revisa esta lista en cada turno para confirmar
                    que ninguno quedo sin comunicar.
                    """)
    @GetMapping("/criticos")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<ResultResponse>>> criticos() {
        return ResponseEntity.ok(ApiResponse.ok("Valores criticos de hoy.",
                resultService.criticosDeHoy()));
    }

    @Operation(summary = "Historial de una prueba en un paciente",
            description = """
                    Devuelve los valores del mas reciente al mas antiguo, para
                    ver la evolucion entre controles.
                    """)
    @GetMapping("/historial/paciente/{idPaciente}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<ResultResponse>>> historial(
            @PathVariable Long idPaciente,
            @RequestParam String codigoPrueba,
            @RequestParam(defaultValue = "12") int limite) {

        return ResponseEntity.ok(ApiResponse.ok("Historial de la prueba.",
                resultService.historial(idPaciente, codigoPrueba, limite)));
    }
}
