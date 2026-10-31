package com.medcore.analytics.controller;

import com.medcore.analytics.domain.document.DiagnosisRecord;
import com.medcore.analytics.domain.document.OutbreakAlert;
import com.medcore.analytics.domain.enums.ReportPeriod;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.analytics.service.ClinicalSurveillanceService;
import com.medcore.analytics.service.OutbreakDetectionService;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Vigilancia por codigo CIE-10, sobre los diagnosticos del medico.
 *
 * <p>Es la otra mitad de la vigilancia: {@code /api/epidemiology} solo ve lo
 * que pasa por el laboratorio, asi que una enfermedad que se diagnostica por
 * clinica y no por analitica no aparecia por ningun sitio.
 *
 * <p>Las dos fuentes no se suman en un mismo recuento: el mismo paciente puede
 * tener un resultado anormal y un diagnostico del mismo episodio.
 */
@Tag(name = "Vigilancia clinica",
        description = "Morbilidad y brotes por codigo CIE-10")
@RestController
@RequestMapping("/api/surveillance")
public class ClinicalSurveillanceController {

    private final ClinicalSurveillanceService surveillanceService;
    private final OutbreakDetectionService outbreakService;

    public ClinicalSurveillanceController(ClinicalSurveillanceService surveillanceService,
                                          OutbreakDetectionService outbreakService) {
        this.surveillanceService = surveillanceService;
        this.outbreakService = outbreakService;
    }

    @Operation(summary = "Codigos mas diagnosticados del periodo",
            description = "La morbilidad del centro: de que se enferma la gente "
                    + "que atiende. Cuenta diagnosticos y pacientes distintos, "
                    + "porque un paciente que vuelve por lo mismo no es un caso "
                    + "nuevo.")
    @GetMapping("/morbilidad")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<ConteoPorClave>>> morbilidad(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(
                "Morbilidad: " + periodo.etiqueta() + ".",
                surveillanceService.morbilidad(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Casos por grupo de edad",
            description = "Distingue una gripe estacional de algo que hay que "
                    + "mirar: la misma cifra concentrada en menores de cinco "
                    + "anios no significa lo mismo que repartida entre adultos.")
    @GetMapping("/grupos-edad")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<ConteoPorClave>>> porGrupoDeEdad(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(
                "Casos por grupo de edad: " + periodo.etiqueta() + ".",
                surveillanceService.porGrupoDeEdad(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Casos por zona, para el mapa de calor clinico")
    @GetMapping("/zonas")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<ConteoPorClave>>> porZona(
            @RequestParam(defaultValue = "SEMANAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(
                "Casos por zona: " + periodo.etiqueta() + ".",
                surveillanceService.porZona(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Curva epidemica: casos por dia",
            description = "La forma de la curva dice mas que el total. Una "
                    + "subida en pocos dias es un brote; la misma cifra "
                    + "repartida en un mes es lo normal.")
    @GetMapping("/curva")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<ConteoPorFecha>>> curvaEpidemica(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(
                "Curva epidemica: " + periodo.etiqueta() + ".",
                surveillanceService.curvaEpidemica(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Enfermedades de notificacion obligatoria del periodo",
            description = "La lista que hay que mandar al MINSA. Exigen aviso "
                    + "con un solo caso, sin esperar a que se acumule ningun "
                    + "umbral, asi que esta consulta no filtra por numero de "
                    + "casos.")
    @GetMapping("/notificables")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<DiagnosisRecord>>> notificables(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        List<DiagnosisRecord> casos =
                surveillanceService.notificables(periodo.desde(hasta), hasta);

        return ResponseEntity.ok(ApiResponse.ok(
                casos.size() + " caso(s) de notificacion obligatoria.", casos));
    }

    @Operation(summary = "Casos de una zona y un codigo CIE-10",
            description = "Para revisar un brote detectado: que pacientes lo "
                    + "componen y en que fechas.")
    @GetMapping("/zona/{zona}/cie10/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<DiagnosisRecord>>> casosDe(
            @PathVariable String zona,
            @PathVariable String codigo,
            @RequestParam(defaultValue = "SEMANAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Casos de " + codigo + " en " + zona + ".",
                surveillanceService.casosDe(zona, codigo, periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Zonas con algun diagnostico registrado")
    @GetMapping("/zonas/listado")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<List<String>>> zonas() {
        return ResponseEntity.ok(ApiResponse.ok("Zonas con diagnosticos.",
                surveillanceService.zonas()));
    }

    @Operation(summary = "Cifras del periodo")
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
    public ResponseEntity<ApiResponse<ClinicalSurveillanceService.Resumen>> resumen(
            @RequestParam(defaultValue = "MENSUAL") ReportPeriod periodo) {

        LocalDate hasta = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Resumen: " + periodo.etiqueta() + ".",
                surveillanceService.resumen(periodo.desde(hasta), hasta)));
    }

    @Operation(summary = "Barrido de brotes por codigo CIE-10",
            description = "Recorre los diagnosticos de la ventana y abre o "
                    + "actualiza las alertas. Se ejecuta a mano desde aqui; la "
                    + "deteccion normal se dispara al registrar cada "
                    + "diagnostico, porque un brote visto doce horas despues "
                    + "sirve de poco.")
    @PostMapping("/brotes/barrido")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OutbreakAlert>>> barrido(
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false)
            String requestId) {

        List<OutbreakAlert> novedades = outbreakService.detectarClinicos(requestId);

        return ResponseEntity.ok(ApiResponse.ok(
                novedades.isEmpty()
                        ? "Barrido sin novedades."
                        : novedades.size() + " alerta(s) abierta(s) o actualizada(s).",
                novedades));
    }
}
