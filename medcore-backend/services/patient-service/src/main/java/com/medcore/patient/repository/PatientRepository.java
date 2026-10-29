package com.medcore.patient.repository;

import com.medcore.patient.domain.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Repositorio de fichas.
 *
 * <p>Extiende JpaSpecificationExecutor para las busquedas combinadas que
 * arma {@link com.medcore.patient.repository.specification.PatientSpecification}.
 */
public interface PatientRepository extends JpaRepository<Patient, Long>,
        JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByDni(String dni);

    boolean existsByDni(String dni);
}
