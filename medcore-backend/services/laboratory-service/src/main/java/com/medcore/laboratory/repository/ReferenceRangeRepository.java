package com.medcore.laboratory.repository;

import com.medcore.laboratory.domain.entity.ReferenceRange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceRangeRepository extends JpaRepository<ReferenceRange, Long> {

    /**
     * Todos los rangos de una prueba.
     *
     * <p>Se traen todos y la seleccion por sexo y edad la hace
     * ValidationService con {@code aplicaA}, en lugar de filtrarse en SQL. El
     * motivo es que la regla de desempate importa: un rango especifico de
     * sexo debe ganar al generico, y expresar esa prioridad en la consulta
     * resulta mas oscuro que en codigo. Son tres o cuatro filas por prueba.
     */
    List<ReferenceRange> findByCodigoPrueba(String codigoPrueba);
}
