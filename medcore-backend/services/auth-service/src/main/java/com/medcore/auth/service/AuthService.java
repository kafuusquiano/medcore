package com.medcore.auth.service;

import com.medcore.auth.domain.dto.request.LoginRequest;
import com.medcore.auth.domain.dto.request.RefreshTokenRequest;
import com.medcore.auth.domain.dto.response.LoginResponse;

/**
 * Autenticacion: inicio de sesion, renovacion y cierre.
 */
public interface AuthService {

    /**
     * @param ipOrigen    IP del cliente, para la bitacora de accesos
     * @param dispositivo User-Agent del cliente
     * @param requestId   X-Request-Id propagado por el gateway
     */
    LoginResponse login(LoginRequest request, String ipOrigen, String dispositivo, String requestId);

    LoginResponse refrescar(RefreshTokenRequest request);

    /** Revoca el token de acceso y, si se envia, tambien el de refresco. */
    void logout(String accessToken, String refreshToken);
}
