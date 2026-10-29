package com.medcore.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de accesos, para auditoria.
 *
 * <p>Se escribe tanto en los inicios de sesion correctos como en los fallidos:
 * una racha de fallos desde una misma IP es la senal que permite detectar un
 * intento de fuerza bruta.
 *
 * <p>No guarda el token ni la contrasena.
 */
@Entity
@Table(name = "session_log")
public class SessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo si el intento fallo porque el usuario no existe. */
    @Column(name = "user_id")
    private Long userId;

    /** Se registra el identificador usado en el intento, aunque no exista. */
    @Column(name = "identificador", nullable = false, length = 120)
    private String identificador;

    @Column(name = "exitoso", nullable = false)
    private boolean exitoso;

    @Column(name = "motivo", length = 60)
    private String motivo;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "dispositivo", length = 255)
    private String dispositivo;

    @Column(name = "request_id", length = 40)
    private String requestId;

    @Column(name = "fecha_acceso", nullable = false, updatable = false)
    private Instant fechaAcceso;

    protected SessionLog() {
        // Requerido por JPA.
    }

    private SessionLog(Long userId, String identificador, boolean exitoso, String motivo,
                       String ipOrigen, String dispositivo, String requestId) {
        this.userId = userId;
        this.identificador = identificador;
        this.exitoso = exitoso;
        this.motivo = motivo;
        this.ipOrigen = ipOrigen;
        this.dispositivo = recortar(dispositivo);
        this.requestId = requestId;
        this.fechaAcceso = Instant.now();
    }

    public static SessionLog exito(Long userId, String identificador,
                                   String ipOrigen, String dispositivo, String requestId) {
        return new SessionLog(userId, identificador, true, null, ipOrigen, dispositivo, requestId);
    }

    public static SessionLog fallo(Long userId, String identificador, String motivo,
                                   String ipOrigen, String dispositivo, String requestId) {
        return new SessionLog(userId, identificador, false, motivo, ipOrigen, dispositivo, requestId);
    }

    /** El User-Agent puede superar con holgura el ancho de la columna. */
    private static String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() > 255 ? valor.substring(0, 255) : valor;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getIdentificador() {
        return identificador;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getIpOrigen() {
        return ipOrigen;
    }

    public String getDispositivo() {
        return dispositivo;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getFechaAcceso() {
        return fechaAcceso;
    }
}
