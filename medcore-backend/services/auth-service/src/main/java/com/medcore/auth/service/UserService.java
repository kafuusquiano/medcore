package com.medcore.auth.service;

import com.medcore.auth.domain.dto.request.CreateUserRequest;
import com.medcore.auth.domain.dto.response.UserResponse;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.commons.dto.PageableResponse;
import org.springframework.data.domain.Pageable;

/**
 * Gestion de usuarios. Todas las operaciones son privativas de ADMIN, salvo
 * la creacion automatica desde el evento patient.created.
 */
public interface UserService {

    UserResponse crear(CreateUserRequest request);

    UserResponse obtenerPorId(Long id);

    PageableResponse<UserResponse> listar(RoleType rol, Pageable pageable);

    UserResponse activar(Long id);

    UserResponse desactivar(Long id);

    /**
     * Crea la cuenta de un paciente a partir del evento patient.created.
     * Es idempotente: si ya existe cuenta para ese paciente, no hace nada.
     */
    void crearCuentaPaciente(Long idPaciente, String dni, String correo,
                             String nombres, String apellidos, String telefono);
}
