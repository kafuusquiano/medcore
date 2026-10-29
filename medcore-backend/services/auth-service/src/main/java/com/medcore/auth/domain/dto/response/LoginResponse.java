package com.medcore.auth.domain.dto.response;

/**
 * Resultado de un inicio de sesion correcto.
 *
 * @param accessToken     token de acceso, de vida corta
 * @param refreshToken    token de refresco, de vida larga
 * @param tipoToken       siempre "Bearer"
 * @param expiraEnSegundos vigencia del token de acceso
 * @param usuario         datos del usuario autenticado
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tipoToken,
        long expiraEnSegundos,
        UserResponse usuario
) {
    public static LoginResponse de(String accessToken, String refreshToken,
                                   long expiraEnSegundos, UserResponse usuario) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiraEnSegundos, usuario);
    }

    @Override
    public String toString() {
        return "LoginResponse[usuario=" + (usuario == null ? null : usuario.dni()) + "]";
    }
}
