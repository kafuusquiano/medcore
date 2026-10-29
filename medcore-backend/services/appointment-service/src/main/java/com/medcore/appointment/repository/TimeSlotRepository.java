package com.medcore.appointment.repository;

import com.medcore.appointment.domain.entity.TimeSlot;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    /**
     * Lee el slot con bloqueo pesimista de escritura, es decir
     * {@code SELECT ... FOR UPDATE}.
     *
     * <p>Es la defensa contra la doble reserva. Dos peticiones que piden el
     * mismo horario a la vez quedan serializadas: la segunda espera a que la
     * primera confirme y entonces ve el slot ya ocupado, de modo que recibe un
     * conflicto claro en lugar de un error de concurrencia.
     *
     * <p>El timeout evita que una peticion se quede esperando indefinidamente
     * si otra transaccion retiene el bloqueo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT s FROM TimeSlot s WHERE s.id = :id")
    Optional<TimeSlot> buscarParaReservar(@Param("id") Long id);

    List<TimeSlot> findByIdMedicoAndFechaOrderByHoraInicio(Long idMedico, LocalDate fecha);

    List<TimeSlot> findByIdMedicoAndFechaAndOcupadoFalseOrderByHoraInicio(Long idMedico, LocalDate fecha);

    Optional<TimeSlot> findByIdMedicoAndFechaAndHoraInicio(Long idMedico, LocalDate fecha, LocalTime horaInicio);

    boolean existsByIdMedicoAndFecha(Long idMedico, LocalDate fecha);

    /** Slots consecutivos a partir de una hora; lo usa el tipo EMERGENCY. */
    @Query("""
            SELECT s FROM TimeSlot s
            WHERE s.idMedico = :idMedico
              AND s.fecha = :fecha
              AND s.horaInicio >= :desde
            ORDER BY s.horaInicio
            """)
    List<TimeSlot> buscarDesde(@Param("idMedico") Long idMedico,
                               @Param("fecha") LocalDate fecha,
                               @Param("desde") LocalTime desde);

    /** Borra los slots libres de fechas pasadas; los ocupados se conservan. */
    @Query("SELECT s FROM TimeSlot s WHERE s.fecha < :fecha AND s.ocupado = false")
    List<TimeSlot> buscarLibresAnterioresA(@Param("fecha") LocalDate fecha);
}
