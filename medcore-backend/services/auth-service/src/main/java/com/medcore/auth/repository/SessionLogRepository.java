package com.medcore.auth.repository;

import com.medcore.auth.domain.entity.SessionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SessionLogRepository extends JpaRepository<SessionLog, Long> {

    Page<SessionLog> findByUserIdOrderByFechaAccesoDesc(Long userId, Pageable pageable);

    /**
     * Intentos fallidos desde una IP en una ventana de tiempo.
     * Soportado por el indice parcial ix_session_log_fallos.
     */
    @Query("""
            SELECT COUNT(s) FROM SessionLog s
            WHERE s.ipOrigen = :ip
              AND s.exitoso = false
              AND s.fechaAcceso >= :desde
            """)
    long contarFallosRecientesPorIp(@Param("ip") String ip, @Param("desde") Instant desde);
}
