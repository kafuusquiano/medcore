package com.medcore.notification.repository;

import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Busca el aviso de un evento por un canal.
     *
     * <p>Hace idempotente el consumo: Kafka entrega al menos una vez, y sin
     * esto un reenvio mandaria el mismo correo dos veces al paciente. El
     * indice unico de V1 es la garantia en base.
     */
    Optional<Notification> findByEventIdAndTipoAndCanal(
            String eventId, NotificationType tipo, NotificationChannel canal);

    boolean existsByEventIdAndTipoAndCanal(
            String eventId, NotificationType tipo, NotificationChannel canal);

    /**
     * Cola de envio: lo pendiente o fallido cuya hora ya llego y que todavia
     * tiene intentos.
     *
     * <p>El tope de intentos se pasa como parametro porque vive en la
     * configuracion (MC3): ajustarlo no deberia exigir recompilar.
     *
     * <p>Ordena por urgencia y luego por antiguedad: un valor critico sale
     * antes que un recordatorio de cita que llevaba mas tiempo esperando.
     */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.estado IN (com.medcore.notification.domain.enums.NotificationStatus.PENDIENTE,
                              com.medcore.notification.domain.enums.NotificationStatus.FALLIDA)
             AND n.fechaProgramada <= :ahora
             AND n.intentos < :maxIntentos
           ORDER BY n.fechaProgramada ASC
           """)
    List<Notification> colaDeEnvio(@Param("ahora") Instant ahora,
                                   @Param("maxIntentos") int maxIntentos,
                                   Pageable pageable);

    /** Bandeja de un paciente: sus avisos in-app, del mas reciente al mas antiguo. */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.idPaciente = :idPaciente
             AND n.canal = com.medcore.notification.domain.enums.NotificationChannel.IN_APP
           ORDER BY n.fechaCreacion DESC
           """)
    Page<Notification> bandejaDePaciente(@Param("idPaciente") Long idPaciente, Pageable pageable);

    /** Bandeja de un usuario interno. */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.idUsuario = :idUsuario
             AND n.canal = com.medcore.notification.domain.enums.NotificationChannel.IN_APP
           ORDER BY n.fechaCreacion DESC
           """)
    Page<Notification> bandejaDeUsuario(@Param("idUsuario") Long idUsuario, Pageable pageable);

    /**
     * Avisos in-app sin leer de un paciente.
     *
     * <p>Cuenta los ENVIADA y no los PENDIENTE: un aviso in-app se considera
     * entregado al guardarse, asi que PENDIENTE solo dura hasta que el
     * dispatcher lo procesa. Contarlos daria un numero que parpadea.
     */
    long countByIdPacienteAndCanalAndEstado(
            Long idPaciente, NotificationChannel canal, NotificationStatus estado);

    long countByIdUsuarioAndCanalAndEstado(
            Long idUsuario, NotificationChannel canal, NotificationStatus estado);

    Page<Notification> findByEstadoOrderByFechaCreacionDesc(
            NotificationStatus estado, Pageable pageable);

    Page<Notification> findByTipoOrderByFechaCreacionDesc(
            NotificationType tipo, Pageable pageable);

    /**
     * Avisos que agotaron sus intentos y nunca llegaron.
     *
     * <p>Es la consulta que importa de verdad en un sistema de avisos: si el
     * correo de un valor critico no salio, alguien tiene que verlo aqui y
     * llamar por telefono.
     */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.estado = com.medcore.notification.domain.enums.NotificationStatus.FALLIDA
             AND n.intentos >= :maxIntentos
           ORDER BY n.fechaActualizacion DESC
           """)
    List<Notification> agotados(@Param("maxIntentos") int maxIntentos);

    long countByEstado(NotificationStatus estado);

    @Query("""
           SELECT COUNT(n) FROM Notification n
           WHERE n.estado = com.medcore.notification.domain.enums.NotificationStatus.FALLIDA
             AND n.intentos >= :maxIntentos
           """)
    long contarAgotados(@Param("maxIntentos") int maxIntentos);
}
