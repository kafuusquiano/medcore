package com.medcore.medical.repository;

import com.medcore.medical.domain.entity.Diagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    List<Diagnosis> findByAtencionId(Long idAtencion);

    boolean existsByAtencionIdAndCodigoCie10(Long idAtencion, String codigoCie10);

    /**
     * Casos notificables en un intervalo, para el reporte a la DIRESA.
     * Lo respalda el indice parcial ix_diagnosis_notificable.
     */
    @Query("""
            SELECT d FROM Diagnosis d
            WHERE d.notificable = true
              AND d.fechaRegistro BETWEEN :desde AND :hasta
            ORDER BY d.fechaRegistro DESC
            """)
    List<Diagnosis> notificablesEntre(@Param("desde") Instant desde,
                                      @Param("hasta") Instant hasta);

    /** Conteo por codigo, base de los indicadores epidemiologicos. */
    @Query("""
            SELECT d.codigoCie10, d.descripcionCie10, COUNT(d)
            FROM Diagnosis d
            WHERE d.fechaRegistro BETWEEN :desde AND :hasta
            GROUP BY d.codigoCie10, d.descripcionCie10
            ORDER BY COUNT(d) DESC
            """)
    List<Object[]> conteoPorCodigo(@Param("desde") Instant desde,
                                   @Param("hasta") Instant hasta);
}
