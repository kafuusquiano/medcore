package com.medcore.auth.repository;

import com.medcore.auth.domain.entity.User;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByDni(String dni);

    boolean existsByDni(String dni);

    boolean existsByCorreo(String correo);

    boolean existsByIdPaciente(Long idPaciente);

    /**
     * Busca por DNI o por correo, que es como funciona el login: el usuario
     * escribe uno u otro en el mismo campo.
     *
     * <p>El correo se compara en minusculas para que no dependa de como lo
     * escriba el usuario; el indice ix_users_correo_lower cubre esta consulta.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.dni = :identificador
               OR LOWER(u.correo) = LOWER(:identificador)
            """)
    Optional<User> buscarPorDniOCorreo(@Param("identificador") String identificador);

    Page<User> findByEstado(UserStatus estado, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.nombre = :rol")
    Page<User> buscarPorRol(@Param("rol") RoleType rol, Pageable pageable);
}
