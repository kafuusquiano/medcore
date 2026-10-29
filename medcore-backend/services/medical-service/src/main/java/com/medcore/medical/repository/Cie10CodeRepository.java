package com.medcore.medical.repository;

import com.medcore.medical.domain.entity.Cie10Code;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Cie10CodeRepository extends JpaRepository<Cie10Code, String> {

    /**
     * Busca por codigo o por texto de la descripcion, que es como el medico
     * localiza un diagnostico cuando no recuerda el codigo exacto.
     */
    @Query("""
            SELECT c FROM Cie10Code c
            WHERE UPPER(c.codigo) LIKE UPPER(CONCAT(:texto, '%'))
               OR LOWER(c.descripcion) LIKE LOWER(CONCAT('%', :texto, '%'))
            ORDER BY c.codigo
            """)
    Page<Cie10Code> buscar(@Param("texto") String texto, Pageable pageable);
}
