package com.medcore.appointment.repository;

import com.medcore.appointment.domain.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    Optional<DoctorSchedule> findByIdMedicoAndActivoTrue(Long idMedico);

    boolean existsByIdMedicoAndActivoTrue(Long idMedico);

    List<DoctorSchedule> findByActivoTrue();

    List<DoctorSchedule> findByEspecialidadIgnoreCaseAndActivoTrue(String especialidad);
}
