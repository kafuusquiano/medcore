package com.medcore.auth.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de inicio de sesion.
 *
 * @param identificador DNI o correo; se acepta cualquiera de los dos
 * @param password      contrasena en texto plano, solo en transito sobre HTTPS
 */
public record LoginRequest(

        @NotBlank(message = "El DNI o correo es obligatorio")
        @Size(max = 120, message = "El identificador no puede superar los 120 caracteres")
        String identificador,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
        String password
) {
    /** No expone la contrasena, para que no termine en un log de validacion. */
    @Override
    public String toString() {
        return "LoginRequest[identificador=" + identificador + "]";
    }
}
