package com.medcore.commons.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagina de resultados, para no exponer la clase {@code Page} de Spring Data
 * en el contrato de la API: su serializacion cambia entre versiones y arrastra
 * campos internos del paginador.
 *
 * @param contenido  elementos de esta pagina
 * @param pagina     indice de la pagina, desde 0
 * @param tamano     elementos por pagina
 * @param totalElementos total de elementos en todas las paginas
 * @param totalPaginas   numero de paginas
 * @param primera    indica si es la primera pagina
 * @param ultima     indica si es la ultima pagina
 */
public record PageableResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        boolean primera,
        boolean ultima
) {

    public static <T> PageableResponse<T> de(Page<T> page) {
        return new PageableResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** Convierte cada entidad de la pagina a su DTO antes de responder. */
    public static <E, T> PageableResponse<T> de(Page<E> page, Function<E, T> conversor) {
        return new PageableResponse<>(
                page.getContent().stream().map(conversor).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
