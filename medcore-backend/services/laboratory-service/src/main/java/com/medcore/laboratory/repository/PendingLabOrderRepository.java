package com.medcore.laboratory.repository;

import com.medcore.laboratory.domain.entity.PendingLabOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PendingLabOrderRepository extends JpaRepository<PendingLabOrder, Long> {

    /**
     * Cola de espera del laboratorio.
     *
     * <p>Las urgentes van primero. El orden descendente de prioridad funciona
     * porque alfabeticamente URGENTE va despues de RUTINA; se ordena por el
     * texto y no por un enum porque la prioridad la define medical-service y
     * aqui solo se recibe como cadena.
     */
    @Query("""
           SELECT o FROM PendingLabOrder o
           WHERE o.atendida = FALSE
           ORDER BY o.prioridad DESC, o.fechaEvento ASC
           """)
    List<PendingLabOrder> cola(Pageable pageable);

    List<PendingLabOrder> findByDniPacienteAndAtendidaFalseOrderByFechaEventoAsc(String dni);

    List<PendingLabOrder> findByIdPacienteOrderByFechaEventoDesc(Long idPaciente);

    long countByAtendidaFalse();

    @Query("SELECT COUNT(o) FROM PendingLabOrder o WHERE o.atendida = FALSE AND o.prioridad = 'URGENTE'")
    long contarUrgentesPendientes();
}
