package com.medcore.inventory.repository;

import com.medcore.inventory.domain.entity.TestReagentUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestReagentUsageRepository extends JpaRepository<TestReagentUsage, Long> {

    /**
     * Reactivos activos que gasta una prueba, con su reactivo ya cargado.
     *
     * <p>El JOIN FETCH evita la consulta por cada linea: el descuento necesita
     * el reactivo de cada una, y sin esto una prueba de tres reactivos haria
     * cuatro consultas.
     */
    @Query("""
           SELECT u FROM TestReagentUsage u
           JOIN FETCH u.reactivo r
           WHERE u.codigoPrueba = :codigoPrueba
             AND u.activo = true
             AND r.activo = true
           """)
    List<TestReagentUsage> activosDe(@Param("codigoPrueba") String codigoPrueba);

    /** Todas las lineas, para el mantenimiento del catalogo de consumos. */
    @Query("""
           SELECT u FROM TestReagentUsage u
           JOIN FETCH u.reactivo
           ORDER BY u.codigoPrueba, u.id
           """)
    List<TestReagentUsage> todasConReactivo();

    /** Las pruebas que declaran consumo, para saber cuales descuentan solas. */
    @Query("""
           SELECT DISTINCT u.codigoPrueba FROM TestReagentUsage u
           WHERE u.activo = true
           ORDER BY u.codigoPrueba
           """)
    List<String> pruebasConConsumo();
}
