package com.medcore.auth.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Map;

/**
 * Errores propios de auth-service.
 *
 * <p>Los fabricantes estaticos fijan el codigo y el mensaje en un solo sitio,
 * para que el mismo fallo no se describa de dos formas distintas segun quien
 * lo lance.
 *
 * <p>Nota de seguridad: {@link #credencialesInvalidas()} responde lo mismo
 * tanto si el usuario no existe como si la contrasena es incorrecta. Devolver
 * mensajes distintos permitiria averiguar que DNI estan registrados.
 */
public class AuthException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    public AuthException(String codigo, HttpStatus estado, String mensaje, Map<String, Object> detalles) {
        super(codigo, estado, mensaje, detalles);
    }

    public static AuthException credencialesInvalidas() {
        return new AuthException("CREDENCIALES_INVALIDAS", HttpStatus.UNAUTHORIZED,
                "El DNI o correo y la contrasena no coinciden.");
    }

    public static AuthException cuentaBloqueada(int intentos) {
        return new AuthException("CUENTA_BLOQUEADA", HttpStatus.FORBIDDEN,
                "La cuenta fue bloqueada tras " + intentos
                        + " intentos fallidos. Contacta al administrador.");
    }

    public static AuthException cuentaInactiva() {
        return new AuthException("CUENTA_INACTIVA", HttpStatus.FORBIDDEN,
                "La cuenta esta desactivada. Contacta al administrador.");
    }

    public static AuthException tokenInvalido() {
        return new AuthException("TOKEN_INVALIDO", HttpStatus.UNAUTHORIZED,
                "El token no es valido.");
    }

    public static AuthException tokenExpirado() {
        return new AuthException("TOKEN_EXPIRADO", HttpStatus.UNAUTHORIZED,
                "La sesion expiro. Inicia sesion nuevamente.");
    }

    public static AuthException tokenRevocado() {
        return new AuthException("TOKEN_REVOCADO", HttpStatus.UNAUTHORIZED,
                "La sesion fue cerrada. Inicia sesion nuevamente.");
    }

    public static AuthException noEsRefreshToken() {
        return new AuthException("TOKEN_TIPO_INCORRECTO", HttpStatus.UNAUTHORIZED,
                "Se esperaba un token de refresco.");
    }

    public static AuthException usuarioNoEncontrado(Long id) {
        return new AuthException("USUARIO_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe un usuario con el identificador indicado.",
                Map.of("id", id));
    }

    public static AuthException dniYaRegistrado(String dni) {
        return new AuthException("DNI_YA_REGISTRADO", HttpStatus.CONFLICT,
                "Ya existe un usuario con el DNI " + dni + ".");
    }

    public static AuthException correoYaRegistrado(String correo) {
        return new AuthException("CORREO_YA_REGISTRADO", HttpStatus.CONFLICT,
                "Ya existe un usuario con el correo " + correo + ".");
    }

    public static AuthException rolNoExiste(String rol) {
        return new AuthException("ROL_NO_EXISTE", HttpStatus.UNPROCESSABLE_ENTITY,
                "El rol " + rol + " no esta definido en el sistema.");
    }
}
