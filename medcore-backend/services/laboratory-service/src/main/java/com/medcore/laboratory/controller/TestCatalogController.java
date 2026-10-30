package com.medcore.laboratory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.laboratory.domain.dto.response.TestCatalogResponse;
import com.medcore.laboratory.service.TestCatalogService;
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
 * Catalogo de pruebas de laboratorio.
 *
 * <p>Lo consulta el medico al emitir una orden y el laboratorista al informar
 * un resultado. Solo lectura: el catalogo se mantiene por migracion, porque
 * anadir una prueba exige definir ademas sus rangos de referencia y sus
 * limites criticos, y dejarla a medias produciria resultados sin interpretar.
 */
@Tag(name = "Catalogo de pruebas", description = "Consulta del catalogo, rangos y limites")
@RestController
@RequestMapping("/api/lab-catalog")
public class TestCatalogController {

    private final TestCatalogService catalogoService;

    public TestCatalogController(TestCatalogService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @Operation(summary = "Pruebas activas del catalogo",
            description = """
                    Cada prueba llega con sus rangos de referencia por sexo y
                    edad, y con sus limites criticos si los tiene.
                    """)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<TestCatalogResponse>>> listar(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String buscar) {

        if (area != null && !area.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok("Pruebas del area " + area + ".",
                    catalogoService.listarPorArea(area)));
        }
        if (buscar != null && !buscar.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok("Resultado de la busqueda.",
                    catalogoService.buscar(buscar)));
        }
        return ResponseEntity.ok(ApiResponse.ok("Catalogo de pruebas.",
                catalogoService.listarActivas()));
    }

    @Operation(summary = "Obtiene una prueba por su codigo")
    @GetMapping("/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<TestCatalogResponse>> obtener(@PathVariable String codigo) {
        return ResponseEntity.ok(ApiResponse.ok("Prueba encontrada.",
                catalogoService.obtenerPorCodigo(codigo)));
    }

    @Operation(summary = "Areas del laboratorio con pruebas activas",
            description = "Para agrupar el catalogo en la interfaz.")
    @GetMapping("/areas")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<String>>> areas() {
        return ResponseEntity.ok(ApiResponse.ok("Areas del laboratorio.",
                catalogoService.areas()));
    }
}
