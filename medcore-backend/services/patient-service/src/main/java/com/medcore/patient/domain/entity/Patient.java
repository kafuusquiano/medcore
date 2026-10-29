package com.medcore.patient.domain.entity;

import com.medcore.patient.domain.enums.BloodType;
import com.medcore.patient.domain.enums.Gender;
import com.medcore.patient.domain.enums.PatientStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Ficha del paciente.
 *
 * <p>Vive en patient_db y ningun otro servicio la consulta directamente
 * (MC1): quien necesite estos datos llama por Feign o escucha los eventos
 * patient.created y patient.updated.
 */
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dni", nullable = false, unique = true, length = 8)
    private String dni;

    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", nullable = false, length = 20)
    private Gender sexo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sangre", nullable = false, length = 15)
    private BloodType tipoSangre = BloodType.DESCONOCIDO;

    @Column(name = "telefono", length = 15)
    private String telefono;

    @Column(name = "correo", length = 120)
    private String correo;

    @Column(name = "direccion", length = 200)
    private String direccion;

    /** Distrito o zona; analytics-service agrupa los brotes por este campo. */
    @Column(name = "zona", length = 80)
    private String zona;

    @Column(name = "contacto_emergencia_nombre", length = 120)
    private String contactoEmergenciaNombre;

    @Column(name = "contacto_emergencia_telefono", length = 15)
    private String contactoEmergenciaTelefono;

    /** Alergias conocidas, en texto libre: el personal clinico las redacta. */
    @Column(name = "alergias", length = 500)
    private String alergias;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private PatientStatus estado = PatientStatus.ACTIVO;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Patient() {
        // Requerido por JPA.
    }

    public Patient(String dni, String nombres, String apellidos, LocalDate fechaNacimiento,
                   Gender sexo, BloodType tipoSangre, String telefono, String correo,
                   String direccion, String zona) {
        this.dni = dni;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.tipoSangre = tipoSangre == null ? BloodType.DESCONOCIDO : tipoSangre;
        this.telefono = telefono;
        this.correo = correo;
        this.direccion = direccion;
        this.zona = zona;
        this.estado = PatientStatus.ACTIVO;
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

    public void actualizarDatos(String nombres, String apellidos, LocalDate fechaNacimiento,
                                Gender sexo, BloodType tipoSangre, String telefono,
                                String correo, String direccion, String zona) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.tipoSangre = tipoSangre == null ? BloodType.DESCONOCIDO : tipoSangre;
        this.telefono = telefono;
        this.correo = correo;
        this.direccion = direccion;
        this.zona = zona;
    }

    public void actualizarContactoEmergencia(String nombre, String telefono) {
        this.contactoEmergenciaNombre = nombre;
        this.contactoEmergenciaTelefono = telefono;
    }

    public void registrarAlergias(String alergias) {
        this.alergias = alergias;
    }

    public void desactivar() {
        this.estado = PatientStatus.INACTIVO;
    }

    public void reactivar() {
        // Una ficha cerrada por fallecimiento no se reabre.
        if (this.estado == PatientStatus.FALLECIDO) {
            throw new IllegalStateException("No se puede reactivar la ficha de un paciente fallecido");
        }
        this.estado = PatientStatus.ACTIVO;
    }

    public void marcarFallecido() {
        this.estado = PatientStatus.FALLECIDO;
    }

    /**
     * Edad en anos cumplidos a dia de hoy.
     *
     * <p>Se calcula y no se almacena: una edad guardada queda desactualizada
     * al dia siguiente del cumpleanos.
     */
    public int edad() {
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    public boolean esMenorDeEdad() {
        return edad() < 18;
    }

    public String nombreCompleto() {
        return (nombres + " " + apellidos).trim();
    }

    public boolean admiteAtencion() {
        return estado.admiteAtencion();
    }

    // ---- Accesores ---------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getDni() {
        return dni;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public Gender getSexo() {
        return sexo;
    }

    public BloodType getTipoSangre() {
        return tipoSangre;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getZona() {
        return zona;
    }

    public String getContactoEmergenciaNombre() {
        return contactoEmergenciaNombre;
    }

    public String getContactoEmergenciaTelefono() {
        return contactoEmergenciaTelefono;
    }

    public String getAlergias() {
        return alergias;
    }

    public PatientStatus getEstado() {
        return estado;
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
        return o instanceof Patient other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Patient[id=" + id + ", dni=" + dni + ", estado=" + estado + "]";
    }
}
