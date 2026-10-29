package com.medcore.medical.repository;

import com.medcore.medical.domain.entity.LabOrderTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabOrderTestRepository extends JpaRepository<LabOrderTest, Long> {

    List<LabOrderTest> findByOrdenId(Long idOrden);
}
