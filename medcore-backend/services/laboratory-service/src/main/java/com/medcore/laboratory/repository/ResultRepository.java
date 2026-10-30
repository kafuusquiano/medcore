package com.medcore.laboratory.repository;

import com.medcore.laboratory.domain.entity.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByMuestraIdOrderByCodigoPrueba(Long idMuestra);

    /**
     * Resultado vigente de una prueba en una muestra.
     *
     * <p>Vigente significa que todavia no ha sido corregido. Corregir no
     * modifica el resultado anterior: anade otro que lo rectifica, asi que la
     * consulta descarta los que ya tienen una correccion encima.
     */
    @Query("""
           SELECT r FROM Result r
           WHERE r.muestra.id = :idMuestra
             AND r.codigoPrueba = :codigoPrueba
             AND r.id NOT IN (SELECT c.idResultadoCorregido FROM Result c
                              WHERE c.idResultadoCorregido IS NOT NULL)
           """)
    Optional<Result> vigenteDe(@Param("idMuestra") Long idMuestra,
                               @Param("codigoPrueba") String codigoPrueba);

    boolean existsByMuestraIdAndCodigoPrueba(Long idMuestra, String codigoPrueba);

    /** Valores criticos de un periodo, que el laboratorio revisa a diario. */
    @Query("""
           SELECT r FROM Result r
           WHERE r.critico = TRUE AND r.fechaResultado >= :desde AND r.fechaResultado < :hasta
           ORDER BY r.fechaResultado DESC
           """)
    List<Result> criticosEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    Page<Result> findByCodigoPruebaOrderByFechaResultadoDesc(String codigoPrueba, Pageable pageable);

    /** Historial de una prueba en un paciente, para ver su evolucion. */
    @Query("""
           SELECT r FROM Result r
           WHERE r.muestra.idPaciente = :idPaciente AND r.codigoPrueba = :codigoPrueba
           ORDER BY r.fechaResultado DESC
           """)
    List<Result> historialDe(@Param("idPaciente") Long idPaciente,
                             @Param("codigoPrueba") String codigoPrueba,
                             Pageable pageable);

    long countByCriticoTrue();
}
