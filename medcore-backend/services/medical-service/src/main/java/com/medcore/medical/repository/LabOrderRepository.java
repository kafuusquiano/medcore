package com.medcore.medical.repository;

import com.medcore.medical.domain.entity.LabOrder;
import com.medcore.medical.domain.enums.LabOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    @EntityGraph(attributePaths = "pruebas")
    Optional<LabOrder> findWithPruebasById(Long id);

    @EntityGraph(attributePaths = "pruebas")
    Optional<LabOrder> findByCodigo(String codigo);

    Page<LabOrder> findByIdPacienteOrderByFechaEmisionDesc(Long idPaciente, Pageable pageable);

    List<LabOrder> findByAtencionId(Long idAtencion);

    Page<LabOrder> findByEstadoOrderByFechaEmision(LabOrderStatus estado, Pageable pageable);

    /**
     * Cuantas ordenes se emitieron hoy. Lo usa el generador del codigo
     * legible para numerarlas de forma correlativa dentro del dia.
     */
    @Query(value = """
            SELECT COUNT(*) FROM lab_orders
            WHERE DATE(fecha_emision AT TIME ZONE 'UTC') = DATE(NOW() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    long contarEmitidasHoy();
}
