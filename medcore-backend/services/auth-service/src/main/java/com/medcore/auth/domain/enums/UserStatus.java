package com.medcore.auth.domain.enums;

/**
 * Estado de la cuenta. Solo ACTIVE puede iniciar sesion.
 */
public enum UserStatus {

    /** Cuenta operativa. */
    ACTIVE,

    /** Dada de baja por un administrador; no puede iniciar sesion. */
    INACTIVE,

    /** Bloqueada automaticamente tras superar el maximo de intentos fallidos. */
    BLOCKED;

    public boolean puedeIniciarSesion() {
        return this == ACTIVE;
    }
}
