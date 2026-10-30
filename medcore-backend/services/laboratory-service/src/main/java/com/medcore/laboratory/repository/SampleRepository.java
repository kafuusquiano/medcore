package com.medcore.laboratory.repository;

import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.enums.SampleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    Optional<Sample> findByCodigo(String codigo);

    /**
     * Muestra viva de una orden.
     *
     * <p>Excluye las rechazadas: tras rechazar un tubo se registra otro para
     * la misma orden, y el indice unico parcial de V2 lo permite. Sin este
     * filtro la consulta podria devolver mas de una fila y fallar.
     */
    @Query("""
           SELECT m FROM Sample m
           WHERE m.idOrden = :idOrden
             AND m.estado <> com.medcore.laboratory.domain.enums.SampleStatus.RECHAZADA
           """)
    Optional<Sample> muestraVivaDeOrden(@Param("idOrden") Long idOrden);

    boolean existsByIdOrdenAndEstadoNot(Long idOrden, SampleStatus estado);

    Page<Sample> findByIdPacienteOrderByFechaTomaDesc(Long idPaciente, Pageable pageable);

    Page<Sample> findByEstadoOrderByUrgenteDescFechaTomaAsc(SampleStatus estado, Pageable pageable);

    /**
     * Cola de trabajo del laboratorio: lo que queda por procesar.
     *
     * <p>Ordena las urgentes primero y, dentro de cada grupo, por orden de
     * llegada. Es el orden en que el tecnico debe atender los tubos.
     */
    @Query("""
           SELECT m FROM Sample m
           WHERE m.estado IN (com.medcore.laboratory.domain.enums.SampleStatus.REGISTRADA,
                              com.medcore.laboratory.domain.enums.SampleStatus.EN_PROCESO)
           ORDER BY m.urgente DESC, m.fechaToma ASC
           """)
    List<Sample> colaDeTrabajo(Pageable pageable);

    /**
     * Cuenta las muestras del dia para armar el correlativo del codigo.
     *
     * <p>Con una sola instancia del servicio basta. Al escalar a dos haria
     * falta una secuencia de Postgres: dos peticiones simultaneas pueden
     * contar lo mismo y proponer el mismo codigo. El indice unico sobre
     * codigo evita el duplicado en base, pero una de las dos peticiones
     * fallaria.
     */
    @Query("SELECT COUNT(m) FROM Sample m WHERE m.fechaToma >= :desde AND m.fechaToma < :hasta")
    long contarEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    long countByEstado(SampleStatus estado);
}
