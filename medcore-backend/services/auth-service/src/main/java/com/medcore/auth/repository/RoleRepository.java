package com.medcore.auth.repository;

import com.medcore.auth.domain.entity.Role;
import com.medcore.auth.domain.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNombre(RoleType nombre);
}
