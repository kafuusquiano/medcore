package com.medcore.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validacion y lectura de los JWT que emite auth-service.
 *
 * <p>El gateway es el unico componente que verifica la firma. Los
 * microservicios confian en las cabeceras que este propaga (MC4).
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Claim donde auth-service coloca el rol del usuario. */
    private static final String CLAIM_ROL = "rol";

    private final SecretKey clave;

    public JwtUtil(@Value("${jwt.secret}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        // HS512 exige al menos 512 bits de clave. Fallar aqui, al arrancar, es
        // preferible a descubrirlo en el primer login.
        if (bytes.length < 64) {
            throw new IllegalStateException(
                    "jwt.secret debe tener al menos 64 caracteres; se recibieron " + bytes.length
                            + ". Genera uno con: openssl rand -base64 64");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * Verifica firma y vigencia del token.
     *
     * @return los claims si el token es valido
     * @throws JwtException si la firma no coincide, el token expiro o esta malformado
     */
    public Claims validarYLeer(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expirado para el subject {}", e.getClaims().getSubject());
            throw e;
        } catch (JwtException e) {
            // No se registra el token: acabaria en los logs un credencial valido.
            log.debug("Token invalido: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    /** Identificador del usuario, que viaja en el subject. */
    public String extraerUserId(Claims claims) {
        return claims.getSubject();
    }

    /** Rol del usuario: ADMIN, MEDICO, RECEPCIONISTA, LABORATORISTA o PACIENTE. */
    public String extraerRol(Claims claims) {
        return claims.get(CLAIM_ROL, String.class);
    }
}
