package com.medcore.auth.service;

import com.medcore.auth.domain.entity.User;

/**
 * Emision y validacion de los JWT de MedCore.
 *
 * <p>El gateway valida la firma de estos tokens con la misma clave, asi que
 * cualquier cambio en los claims debe acordarse con JwtUtil del gateway.
 */
public interface JwtService {

    /** Token de acceso, de vida corta. Lleva el claim {@code rol}. */
    String generarAccessToken(User usuario);

    /** Token de refresco, de vida larga. Lleva {@code tipo=refresh}. */
    String generarRefreshToken(User usuario);

    /**
     * Valida un token de refresco y devuelve el id del usuario.
     *
     * @throws com.medcore.auth.exception.AuthException si esta expirado,
     *         revocado, es invalido o no es de tipo refresh
     */
    Long validarRefreshToken(String token);

    /** Revoca un token hasta su expiracion natural, guardandolo en Redis. */
    void revocar(String token);

    /** Indica si un token fue revocado por un logout previo. */
    boolean estaRevocado(String token);

    long expiracionSegundos();
}
