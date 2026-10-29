package com.medcore.patient.repository;

import com.medcore.patient.domain.entity.MedicalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {

    Page<MedicalHistory> findByPacienteIdOrderByFechaEpisodioDesc(Long pacienteId, Pageable pageable);

    /**
     * Busca un episodio ya registrado por el servicio de origen.
     *
     * <p>Devuelve el episodio y no solo un booleano para que un reintento
     * pueda responder con el registro original en lugar de crear otro.
     * Lo respalda el indice unico ux_history_origen_referencia.
     */
    Optional<MedicalHistory> findByOrigenAndReferenciaExterna(String origen, String referenciaExterna);
}
