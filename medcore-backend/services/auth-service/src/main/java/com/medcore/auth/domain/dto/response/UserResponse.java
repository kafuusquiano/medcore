package com.medcore.auth.domain.dto.response;

import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.domain.enums.UserStatus;

import java.time.Instant;

/**
 * Vista publica de un usuario. No incluye el hash de la contrasena ni el
 * contador de intentos fallidos.
 */
public record UserResponse(
        Long id,
        String dni,
        String correo,
        String nombres,
        String apellidos,
        String telefono,
        RoleType rol,
        UserStatus estado,
        Long idPaciente,
        Instant ultimoAcceso,
        Instant fechaCreacion
) {
}
