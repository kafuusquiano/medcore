package com.medcore.medical.repository;

import com.medcore.medical.domain.entity.MedicalAttention;
import com.medcore.medical.domain.enums.AttentionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MedicalAttentionRepository extends JpaRepository<MedicalAttention, Long> {

    /**
     * Carga la atencion con sus diagnosticos en una sola consulta.
     * Sin el EntityGraph, mostrar una atencion dispararia una consulta
     * adicional por la coleccion perezosa.
     */
    @EntityGraph(attributePaths = "diagnosticos")
    Optional<MedicalAttention> findWithDiagnosticosById(Long id);

    Page<MedicalAttention> findByIdPacienteOrderByFechaAtencionDesc(Long idPaciente, Pageable pageable);

    Page<MedicalAttention> findByIdMedicoOrderByFechaAtencionDesc(Long idMedico, Pageable pageable);

    Page<MedicalAttention> findByEstadoOrderByFechaAtencionDesc(AttentionStatus estado, Pageable pageable);

    /** Atencion viva de una cita; impide registrar dos por la misma. */
    @Query("""
            SELECT a FROM MedicalAttention a
            WHERE a.idCita = :idCita AND a.estado <> 'ANULADA'
            """)
    Optional<MedicalAttention> atencionVivaDeCita(@Param("idCita") Long idCita);

    /** Atenciones abiertas de un medico; las que debe cerrar antes de irse. */
    List<MedicalAttention> findByIdMedicoAndEstado(Long idMedico, AttentionStatus estado);

    @Query("""
            SELECT a FROM MedicalAttention a
            WHERE a.estado = 'EN_CURSO' AND a.fechaAtencion < :limite
            """)
    List<MedicalAttention> abiertasDesdeAntesDe(@Param("limite") Instant limite);
}
