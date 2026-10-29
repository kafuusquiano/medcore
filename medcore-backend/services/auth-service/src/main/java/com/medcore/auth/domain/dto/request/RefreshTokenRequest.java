package com.medcore.auth.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Peticion de renovacion del token de acceso.
 *
 * @param refreshToken token de refresco emitido en el login
 */
public record RefreshTokenRequest(

        @NotBlank(message = "El token de refresco es obligatorio")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[***]";
    }
}
