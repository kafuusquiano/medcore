package com.medcore.inventory.repository;

import com.medcore.inventory.domain.entity.MovementLog;
import com.medcore.inventory.domain.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MovementLogRepository extends JpaRepository<MovementLog, Long> {

    Page<MovementLog> findByStockIdOrderByFechaMovimientoDesc(Long idStock, Pageable pageable);

    Page<MovementLog> findByCodigoReactivoOrderByFechaMovimientoDesc(String codigo, Pageable pageable);

    Page<MovementLog> findAllByOrderByFechaMovimientoDesc(Pageable pageable);

    /** Trazabilidad inversa: que material consumio una muestra concreta. */
    List<MovementLog> findByCodigoMuestraOrderByFechaMovimientoAsc(String codigoMuestra);

    @Query("""
           SELECT m FROM MovementLog m
           WHERE m.fechaMovimiento >= :desde AND m.fechaMovimiento < :hasta
           ORDER BY m.fechaMovimiento DESC
           """)
    List<MovementLog> movimientosEntre(@Param("desde") Instant desde,
                                       @Param("hasta") Instant hasta);

    /**
     * Consumo de un reactivo en un periodo.
     *
     * <p>Devuelve un valor positivo aunque las salidas se guarden con signo
     * negativo: quien pregunta cuanto se consumio espera una cantidad, no un
     * delta.
     */
    @Query("""
           SELECT COALESCE(ABS(SUM(m.cantidad)), 0) FROM MovementLog m
           WHERE m.codigoReactivo = :codigo
             AND m.tipo = com.medcore.inventory.domain.enums.MovementType.SALIDA
             AND m.fechaMovimiento >= :desde AND m.fechaMovimiento < :hasta
           """)
    int consumoEntre(@Param("codigo") String codigo,
                     @Param("desde") Instant desde,
                     @Param("hasta") Instant hasta);

    /**
     * Si un resultado ya descontó reactivo.
     *
     * <p>Es la comprobacion de idempotencia del consumo automatico: Kafka
     * entrega al menos una vez, y un descuento doble deja el stock del sistema
     * por debajo del real, pidiendo reposicion de algo que hay.
     */
    boolean existsByIdResultado(Long idResultado);

    /** Los descuentos de un resultado, para auditar que se gasto en una prueba. */
    List<MovementLog> findByIdResultadoOrderByFechaMovimientoAsc(Long idResultado);

    long countByTipo(MovementType tipo);
}
