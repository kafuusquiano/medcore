package com.medcore.laboratory.repository;

import com.medcore.laboratory.domain.entity.TestCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Catalogo de pruebas.
 *
 * <p>La clave primaria es el codigo y no un identificador generado: el codigo
 * es el que viaja en las ordenes y en las tramas de los equipos, y asignar
 * otro numero obligaria a traducir en cada extremo.
 */
public interface TestCatalogRepository extends JpaRepository<TestCatalog, String> {

    List<TestCatalog> findByActivaTrueOrderByAreaAscNombreAsc();

    List<TestCatalog> findByAreaAndActivaTrueOrderByNombreAsc(String area);

    @Query("""
           SELECT p FROM TestCatalog p
           WHERE p.activa = TRUE AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
           ORDER BY p.nombre
           """)
    List<TestCatalog> buscarPorNombre(@Param("texto") String texto);

    @Query("SELECT DISTINCT p.area FROM TestCatalog p WHERE p.activa = TRUE ORDER BY p.area")
    List<String> areas();
}
