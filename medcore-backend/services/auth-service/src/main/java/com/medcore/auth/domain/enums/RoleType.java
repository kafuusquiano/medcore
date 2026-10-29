package com.medcore.auth.domain.enums;

/**
 * Roles del sistema. El nombre viaja en el claim "rol" del JWT y el gateway
 * lo reenvia en X-User-Role (MC4).
 *
 * <p>Los valores deben coincidir con los que inserta
 * V1__create_roles_users.sql y con los que usa el frontend en useRoleGuard.
 */
public enum RoleType {

    /** Administra usuarios, catalogos y parametros del sistema. */
    ADMIN,

    /** Registra pacientes y gestiona la agenda de citas. */
    RECEPCIONISTA,

    /** Atiende consultas, emite diagnosticos y ordenes de laboratorio. */
    MEDICO,

    /** Registra muestras y resultados de laboratorio. */
    LABORATORISTA,

    /** Consulta sus propias citas y resultados. */
    PACIENTE;

    /** Nombre con el prefijo que exige Spring Security. */
    public String authority() {
        return "ROLE_" + name();
    }
}
