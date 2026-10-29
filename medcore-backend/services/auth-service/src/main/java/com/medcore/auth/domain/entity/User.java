package com.medcore.auth.domain.entity;

import com.medcore.auth.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Usuario del sistema.
 *
 * <p>La contrasena se guarda solo como hash BCrypt. No existe getter que
 * devuelva texto plano ni setter que lo acepte sin cifrar: el cifrado ocurre
 * en el servicio, antes de llamar a {@link #cambiarPassword(String)}.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dni", nullable = false, unique = true, length = 8)
    private String dni;

    @Column(name = "correo", nullable = false, unique = true, length = 120)
    private String correo;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    @Column(name = "telefono", length = 15)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private UserStatus estado = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Intentos fallidos consecutivos; se reinicia en cada login correcto. */
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    /** Id del paciente que origino la cuenta, si vino de patient.created. */
    @Column(name = "id_paciente")
    private Long idPaciente;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /** Bloqueo optimista: evita que dos actualizaciones simultaneas se pisen. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected User() {
        // Requerido por JPA.
    }

    public User(String dni, String correo, String passwordHash, String nombres,
                String apellidos, String telefono, Role role) {
        this.dni = dni;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.telefono = telefono;
        this.role = role;
        this.estado = UserStatus.ACTIVE;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    // ---- Comportamiento de dominio -----------------------------------------

    /**
     * Registra un intento fallido y bloquea la cuenta al alcanzar el maximo.
     *
     * @return true si el intento provoco el bloqueo
     */
    public boolean registrarIntentoFallido(int maximo) {
        this.intentosFallidos++;
        if (this.intentosFallidos >= maximo && this.estado == UserStatus.ACTIVE) {
            this.estado = UserStatus.BLOCKED;
            return true;
        }
        return false;
    }

    public void registrarAccesoExitoso() {
        this.intentosFallidos = 0;
        this.ultimoAcceso = Instant.now();
    }

    public void cambiarPassword(String nuevoHash) {
        this.passwordHash = nuevoHash;
        // Un cambio de contrasena desbloquea la cuenta: es la via de
        // recuperacion tras un bloqueo por intentos fallidos.
        this.intentosFallidos = 0;
        if (this.estado == UserStatus.BLOCKED) {
            this.estado = UserStatus.ACTIVE;
        }
    }

    public void activar() {
        this.estado = UserStatus.ACTIVE;
        this.intentosFallidos = 0;
    }

    public void desactivar() {
        this.estado = UserStatus.INACTIVE;
    }

    public void asignarRol(Role role) {
        this.role = role;
    }

    public void vincularPaciente(Long idPaciente) {
        this.idPaciente = idPaciente;
    }

    public void actualizarDatos(String nombres, String apellidos, String telefono) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.telefono = telefono;
    }

    public boolean puedeIniciarSesion() {
        return estado.puedeIniciarSesion();
    }

    public String nombreCompleto() {
        return (nombres + " " + apellidos).trim();
    }

    // ---- Accesores ---------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getDni() {
        return dni;
    }

    public String getCorreo() {
        return correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getTelefono() {
        return telefono;
    }

    public UserStatus getEstado() {
        return estado;
    }

    public Role getRole() {
        return role;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Long getIdPaciente() {
        return idPaciente;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof User user && id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** No incluye el hash de la contrasena, para que no aparezca en los logs. */
    @Override
    public String toString() {
        return "User[id=" + id + ", dni=" + dni + ", estado=" + estado + "]";
    }
}
