package com.medcore.laboratory.repository;

import com.medcore.laboratory.domain.entity.CriticalValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CriticalValueRepository extends JpaRepository<CriticalValue, Long> {

    /** Una prueba tiene como maximo una fila de limites criticos (UNIQUE en V1). */
    Optional<CriticalValue> findByCodigoPrueba(String codigoPrueba);
}
