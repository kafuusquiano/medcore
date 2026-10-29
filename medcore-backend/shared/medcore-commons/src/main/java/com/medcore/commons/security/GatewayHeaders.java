package com.medcore.commons.security;

/**
 * Cabeceras que el api-gateway propaga a los microservicios (regla MC4).
 *
 * <p>El gateway valida el JWT una sola vez y reenvia la identidad resuelta en
 * estas cabeceras. Los microservicios confian en ellas y no revalidan el token.
 *
 * <p>Esa confianza solo es segura mientras los microservicios no sean
 * alcanzables desde fuera: quien llegue directo a un servicio puede enviar
 * {@code X-User-Role: ADMIN}. En Docker Compose se publica unicamente el
 * puerto 8080 del gateway, y el tunel hacia internet expone solo ese puerto.
 */
public final class GatewayHeaders {

    /** Identificador del usuario extraido del JWT. */
    public static final String USER_ID = "X-User-Id";

    /** Rol del usuario extraido del JWT: ADMIN, MEDICO, RECEPCIONISTA, LABORATORISTA, PACIENTE. */
    public static final String USER_ROLE = "X-User-Role";

    /** UUID generado por el gateway para correlacionar la peticion en los logs. */
    public static final String REQUEST_ID = "X-Request-Id";

    /** Prefijo que Spring Security exige en los nombres de rol. */
    public static final String ROLE_PREFIX = "ROLE_";

    private GatewayHeaders() {
        throw new AssertionError("Clase de constantes, no instanciable");
    }
}
