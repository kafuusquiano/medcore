package com.medcore.patient.repository;

import com.medcore.patient.domain.entity.MedicalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {

    Page<MedicalHistory> findByPacienteIdOrderByFechaEpisodioDesc(Long pacienteId, Pageable pageable);

    /**
     * Comprueba si el episodio ya se registro, para que un reintento del
     * servicio de origen no lo duplique. Lo respalda el indice unico
     * ux_history_origen_referencia.
     */
    boolean existsByOrigenAndReferenciaExterna(String origen, String referenciaExterna);
}
