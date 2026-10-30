package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.dto.response.TestCatalogResponse;

import java.util.List;

/**
 * Consulta del catalogo de pruebas.
 *
 * <p>Solo lectura. El catalogo lo mantiene el laboratorio por migracion o por
 * carga de semilla, no por la API: anadir una prueba exige definir ademas sus
 * rangos y sus limites criticos, y dejarla a medias produciria resultados sin
 * interpretar.
 */
public interface TestCatalogService {

    List<TestCatalogResponse> listarActivas();

    List<TestCatalogResponse> listarPorArea(String area);

    List<TestCatalogResponse> buscar(String texto);

    TestCatalogResponse obtenerPorCodigo(String codigo);

    /** Areas del laboratorio con pruebas activas, para agrupar en la interfaz. */
    List<String> areas();
}
