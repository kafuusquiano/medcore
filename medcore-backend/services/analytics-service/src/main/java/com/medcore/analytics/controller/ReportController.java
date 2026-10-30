package com.medcore.analytics.controller;

import com.medcore.analytics.domain.document.IndicatorSnapshot;
import com.medcore.analytics.domain.dto.response.ReportResponse;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.service.ReportService;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Informes e indicadores archivados.
 *
 * <p>Los informes se calculan en vivo y llevan su hora de generacion: el mismo
 * informe pedido manana puede dar otras cifras si entretanto se corrigio un
 * resultado.
 *
 * <p>Las fotos de indicadores existen justamente para eso: conservan el valor
 * que tenia un indicador cuando se tomo una decision, y no cambian despues.
 */
@Tag(name = "Informes", description = "Informes epidemiologicos e indicadores archivados")
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Informe epidemiologico de un periodo",
            description = """
                    Total de resultados, casos, criticos y positividad, con el
                    desglose por zona y por prueba. La positividad es el
                    indicador que se sigue en vigilancia: no interesa tanto
                    cuantas pruebas se hicieron como que proporcion salio
                    alterada.
                    """)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<ReportResponse>> informe(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        return ResponseEntity.ok(ApiResponse.ok("Informe: " + periodo.etiqueta() + ".",
                reportService.informe(periodo)));
    }

    @Operation(summary = "Informe de un rango concreto",
            description = "El rango no puede pasar de 366 dias.")
    @GetMapping("/rango")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<ReportResponse>> informeEntre(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(ApiResponse.ok("Informe del rango.",
                reportService.informeEntre(desde, hasta)));
    }

    @Operation(summary = "Serie historica de un indicador archivado",
            description = """
                    Los valores con los que se informo en cada momento, que no
                    cambian aunque los registros se corrijan despues.
                    Indicadores disponibles: positividad_global,
                    casos_por_zona, casos_criticos y alertas_abiertas.
                    """)
    @GetMapping("/indicadores/{indicador}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<IndicatorSnapshot>>> fotos(
            @PathVariable String indicador,
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Historico de " + indicador + ".",
                reportService.fotosDe(indicador, periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Ultima foto archivada de un indicador")
    @GetMapping("/indicadores/{indicador}/ultima")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<IndicatorSnapshot>> ultimaFoto(
            @PathVariable String indicador) {

        return ResponseEntity.ok(ApiResponse.ok("Ultima foto de " + indicador + ".",
                reportService.ultimaFotoDe(indicador)));
    }

    @Operation(summary = "Archiva los indicadores del dia a demanda",
            description = """
                    Lo mismo que hace el proceso periodico. Es idempotente por
                    dia: volver a calcularlo sobrescribe la foto del dia en
                    lugar de acumular copias.
                    """)
    @PostMapping("/indicadores/archivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<IndicatorSnapshot>>> archivar(
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Indicadores archivados.",
                reportService.archivarIndicadores(requestId)));
    }
}
