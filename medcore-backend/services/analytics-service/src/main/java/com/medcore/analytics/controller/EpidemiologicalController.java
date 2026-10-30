package com.medcore.analytics.controller;

import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.domain.dto.response.AlertResponse;
import com.medcore.analytics.domain.dto.response.HeatmapPointResponse;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.domain.enums.RiskLevel;
import com.medcore.analytics.mapper.AnalyticsMapper;
import com.medcore.analytics.service.EpidemiologicalService;
import com.medcore.analytics.service.OutbreakDetectionService;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Vigilancia epidemiologica: mapa de calor y alertas de brote.
 *
 * <p>Lo consultan el administrador y el medico. El laboratorista no: su
 * trabajo es el resultado individual, y estos datos son agregados de toda la
 * poblacion atendida.
 *
 * <p>Sobre que se vigila: la deteccion es por PRUEBA DE LABORATORIO con
 * resultado anormal, no por codigo CIE-10, porque medical-service todavia no
 * publica eventos de diagnostico. "Cinco casos de proteina C reactiva alta en
 * la misma zona en siete dias" es una senal valida por si misma.
 */
@Tag(name = "Epidemiologia", description = "Mapa de calor y alertas de brote")
@RestController
@RequestMapping("/api/epidemiology")
public class EpidemiologicalController {

    private final EpidemiologicalService epidemiologicalService;
    private final OutbreakDetectionService outbreakService;
    private final AnalyticsMapper mapper;

    public EpidemiologicalController(EpidemiologicalService epidemiologicalService,
                                     OutbreakDetectionService outbreakService,
                                     AnalyticsMapper mapper) {
        this.epidemiologicalService = epidemiologicalService;
        this.outbreakService = outbreakService;
        this.mapper = mapper;
    }

    @Operation(summary = "Mapa de calor de casos por zona",
            description = """
                    Casos anormales por zona en la ventana indicada. La
                    intensidad viene calculada de 0 a 1, relativa a la zona con
                    mas casos del mismo periodo, para que todos los clientes
                    pinten la misma escala.

                    Las coordenadas son centroides aproximados; las zonas que no
                    figuran en el archivo de referencia salen sin ellas y el
                    panel las muestra solo en la tabla.
                    """)
    @GetMapping("/heatmap")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<HeatmapPointResponse>>> mapaDeCalor(
            @RequestParam(defaultValue = "SEMANAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Mapa de calor: " + periodo.etiqueta() + ".",
                epidemiologicalService.mapaDeCalor(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Mapa de calor de un rango concreto")
    @GetMapping("/heatmap/rango")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<HeatmapPointResponse>>> mapaDeCalorEntre(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(ApiResponse.ok("Mapa de calor del rango.",
                epidemiologicalService.mapaDeCalor(desde, hasta)));
    }

    @Operation(summary = "Casos de una zona",
            description = "Para abrir el detalle de un punto del mapa.")
    @GetMapping("/zona/{zona}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<AnalyticsRecord>>> casosDeZona(
            @PathVariable String zona,
            @RequestParam(defaultValue = "SEMANAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Casos de " + zona + ".",
                epidemiologicalService.casosDeZona(zona, periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Resultados criticos del periodo",
            description = "Los que exigieron aviso inmediato al medico tratante.")
    @GetMapping("/criticos")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<AnalyticsRecord>>> criticos(
            @RequestParam(defaultValue = "SEMANAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Resultados criticos.",
                epidemiologicalService.criticos(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Zonas con registros",
            description = "Para ofrecer el filtro del panel.")
    @GetMapping("/zonas")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<String>>> zonas() {
        return ResponseEntity.ok(ApiResponse.ok("Zonas con registros.",
                epidemiologicalService.zonas()));
    }

    // ---- Alertas de brote --------------------------------------------------

    @Operation(summary = "Alertas de brote abiertas",
            description = "Ordenadas por nivel de riesgo y, dentro de cada nivel, por fecha.")
    @GetMapping("/alertas")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> alertas(
            @RequestParam(required = false) RiskLevel nivel) {

        var alertas = nivel == null
                ? outbreakService.alertasAbiertas()
                : outbreakService.alertasPorNivel(nivel);

        return ResponseEntity.ok(ApiResponse.ok("Alertas abiertas.",
                alertas.stream().map(mapper::aRespuesta).toList()));
    }

    @Operation(summary = "Historial de alertas de una zona",
            description = "Incluye las ya cerradas, para ver si el brote reaparece.")
    @GetMapping("/alertas/zona/{zona}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> alertasDeZona(
            @PathVariable String zona) {

        return ResponseEntity.ok(ApiResponse.ok("Alertas de " + zona + ".",
                outbreakService.alertasDeZona(zona).stream().map(mapper::aRespuesta).toList()));
    }

    @Operation(summary = "Obtiene una alerta")
    @GetMapping("/alertas/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AlertResponse>> alerta(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Alerta encontrada.",
                mapper.aRespuesta(outbreakService.obtenerPorId(id))));
    }

    @Operation(summary = "Cierra una alerta como confirmada o descartada",
            description = """
                    El motivo es obligatorio: que se sepa cuantas alertas se
                    descartaron y por que es lo que permite afinar el umbral.
                    Una alerta cerrada no se reabre; si el brote reaparece se
                    abre otra.
                    """)
    @PatchMapping("/alertas/{id}/resolucion")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<AlertResponse>> resolver(
            @PathVariable String id,
            @Valid @RequestBody ResolucionRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario) {

        return ResponseEntity.ok(ApiResponse.ok("Alerta cerrada.",
                mapper.aRespuesta(outbreakService.resolver(
                        id, request.desenlace(), usuario, request.motivo()))));
    }

    @Operation(summary = "Lanza el barrido de brotes a demanda",
            description = """
                    El mismo barrido que corre periodicamente. Util tras
                    cambiar el umbral en la configuracion, para no esperar al
                    siguiente ciclo.
                    """)
    @PostMapping("/alertas/barrido")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> barrido(
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Barrido ejecutado.",
                outbreakService.detectar(requestId).stream()
                        .map(mapper::aRespuesta).toList()));
    }

    @Operation(summary = "Cuantas alertas hay abiertas y de que gravedad",
            description = "Para el panel.")
    @GetMapping("/alertas/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<OutbreakDetectionService.Resumen>> resumen() {
        return ResponseEntity.ok(ApiResponse.ok("Resumen de alertas.",
                outbreakService.resumen()));
    }

    /**
     * Desenlace de una alerta.
     *
     * <p>Va en el cuerpo y no como parametro de consulta: el motivo puede
     * llevar tildes y espacios, y es texto que queda archivado en la alerta.
     */
    public record ResolucionRequest(
            @NotNull(message = "Indique si la alerta se confirma o se descarta")
            AlertStatus desenlace,

            @NotBlank(message = "Indique por que se cierra la alerta")
            @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres")
            String motivo
    ) {
    }
}
