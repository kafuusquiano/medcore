package com.medcore.analytics.controller;

import com.medcore.analytics.domain.dto.response.TrendResponse;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.service.TrendAnalysisService;
import com.medcore.commons.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Series temporales de casos.
 *
 * <p>Los puntos vienen agrupados segun el periodo: por dia en las ventanas
 * cortas, por semana en un trimestre y por mes en un anio. Una serie anual con
 * un punto por dia son 365 puntos que en una grafica no se leen.
 *
 * <p>Los dias sin datos vienen con ceros y no se omiten: una grafica que salta
 * del dia 3 al dia 9 dibuja una linea recta entre ambos y sugiere una
 * tendencia que no existe.
 */
@Tag(name = "Tendencias", description = "Series temporales de casos y positividad")
@RestController
@RequestMapping("/api/trends")
public class TrendController {

    private final TrendAnalysisService trendService;

    public TrendController(TrendAnalysisService trendService) {
        this.trendService = trendService;
    }

    @Operation(summary = "Tendencia general",
            description = """
                    Incluye la comparacion con el periodo anterior de la misma
                    duracion. Si antes no hubo casos, la variacion viene nula y
                    no cero: pasar de 0 a 5 casos no significa que subio un 500
                    por ciento, significa que no habia con que comparar.
                    """)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<TrendResponse>> general(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        return ResponseEntity.ok(ApiResponse.ok("Tendencia general.",
                trendService.tendenciaGeneral(periodo)));
    }

    @Operation(summary = "Tendencia de una zona")
    @GetMapping("/zona/{zona}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<TrendResponse>> deZona(
            @PathVariable String zona,
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        return ResponseEntity.ok(ApiResponse.ok("Tendencia de " + zona + ".",
                trendService.tendenciaDeZona(zona, periodo)));
    }

    @Operation(summary = "Tendencia de una prueba",
            description = "Para seguir la positividad de una prueba concreta.")
    @GetMapping("/prueba/{codigoPrueba}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<TrendResponse>> dePrueba(
            @PathVariable String codigoPrueba,
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        return ResponseEntity.ok(ApiResponse.ok("Tendencia de " + codigoPrueba + ".",
                trendService.tendenciaDePrueba(codigoPrueba, periodo)));
    }
}
