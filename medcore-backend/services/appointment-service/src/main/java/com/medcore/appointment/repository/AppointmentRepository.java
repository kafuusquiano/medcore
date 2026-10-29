package com.medcore.appointment.repository;

import com.medcore.appointment.domain.entity.Appointment;
import com.medcore.appointment.domain.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Page<Appointment> findByIdPacienteOrderByFechaHoraDesc(Long idPaciente, Pageable pageable);

    /** Agenda de un medico en un intervalo. Es la consulta mas frecuente. */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.idMedico = :idMedico
              AND a.fechaHora BETWEEN :desde AND :hasta
            ORDER BY a.fechaHora
            """)
    List<Appointment> agendaDe(@Param("idMedico") Long idMedico,
                               @Param("desde") LocalDateTime desde,
                               @Param("hasta") LocalDateTime hasta);

    Page<Appointment> findByEstadoOrderByFechaHora(AppointmentStatus estado, Pageable pageable);

    /**
     * Citas vivas de un paciente a partir de un instante.
     * Evita que reserve dos veces el mismo dia sin darse cuenta.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.idPaciente = :idPaciente
              AND a.estado IN ('SCHEDULED', 'CONFIRMED')
              AND a.fechaHora >= :desde
            ORDER BY a.fechaHora
            """)
    List<Appointment> citasVivasDe(@Param("idPaciente") Long idPaciente,
                                   @Param("desde") LocalDateTime desde);

    /** Citas que ya pasaron y nadie cerro; las marca NO_SHOW el planificador. */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.estado IN ('SCHEDULED', 'CONFIRMED')
              AND a.fechaHora < :limite
            """)
    List<Appointment> vencidasSinCerrar(@Param("limite") LocalDateTime limite);
}
