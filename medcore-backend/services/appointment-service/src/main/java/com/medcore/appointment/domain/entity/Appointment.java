package com.medcore.appointment.domain.entity;

import com.medcore.appointment.domain.enums.AppointmentStatus;
import com.medcore.appointment.domain.enums.AppointmentType;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Cita entre un paciente y un medico.
 *
 * <p>Guarda una copia del nombre y el contacto del paciente en el momento de
 * reservar. Es duplicacion deliberada: evita llamar a patient-service para
 * listar la agenda del dia, que es la consulta mas frecuente, y deja constancia
 * de con que datos se reservo. La ficha del paciente sigue siendo la fuente de
 * verdad (MC1).
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_paciente", nullable = false)
    private Long idPaciente;

    @Column(name = "dni_paciente", nullable = false, length = 8)
    private String dniPaciente;

    @Column(name = "nombre_paciente", nullable = false, length = 170)
    private String nombrePaciente;

    @Column(name = "correo_paciente", length = 120)
    private String correoPaciente;

    @Column(name = "telefono_paciente", length = 15)
    private String telefonoPaciente;

    @Column(name = "id_medico", nullable = false)
    private Long idMedico;

    @Column(name = "nombre_medico", nullable = false, length = 120)
    private String nombreMedico;

    @Column(name = "id_slot", nullable = false)
    private Long idSlot;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private AppointmentType tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private AppointmentStatus estado = AppointmentStatus.SCHEDULED;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Appointment() {
        // Requerido por JPA.
    }

    public Appointment(Long idPaciente, String dniPaciente, String nombrePaciente,
                       String correoPaciente, String telefonoPaciente,
                       Long idMedico, String nombreMedico, Long idSlot,
                       LocalDateTime fechaHora, AppointmentType tipo, String motivo) {
        this.idPaciente = idPaciente;
        this.dniPaciente = dniPaciente;
        this.nombrePaciente = nombrePaciente;
        this.correoPaciente = correoPaciente;
        this.telefonoPaciente = telefonoPaciente;
        this.idMedico = idMedico;
        this.nombreMedico = nombreMedico;
        this.idSlot = idSlot;
        this.fechaHora = fechaHora;
        this.tipo = tipo;
        this.estado = AppointmentStatus.SCHEDULED;
        this.motivo = motivo;
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
     * Cambia el estado si la transicion es valida.
     *
     * @throws IllegalStateException si la transicion no esta permitida; el
     *         handler la traduce a 409. Las transiciones validas las define
     *         {@link AppointmentStatus}, en un solo sitio.
     */
    public void cambiarEstado(AppointmentStatus destino) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException(
                    "No se puede pasar de " + estado + " a " + destino);
        }
        this.estado = destino;
    }

    public void confirmar() {
        cambiarEstado(AppointmentStatus.CONFIRMED);
    }

    public void completar() {
        cambiarEstado(AppointmentStatus.COMPLETED);
    }

    public void marcarNoAsistio() {
        cambiarEstado(AppointmentStatus.NO_SHOW);
    }

    public void cancelar(String motivo) {
        cambiarEstado(AppointmentStatus.CANCELLED);
        this.motivoCancelacion = motivo;
    }

    /**
     * Horas que faltan para la cita. Negativo si ya paso.
     * Se usa para exigir una antelacion minima al cancelar.
     */
    public long horasDeAntelacion() {
        return Duration.between(LocalDateTime.now(), fechaHora).toHours();
    }

    public boolean yaPaso() {
        return fechaHora.isBefore(LocalDateTime.now());
    }

    public boolean estaActiva() {
        return !estado.esFinal();
    }

    public Long getId() {
        return id;
    }

    public Long getIdPaciente() {
        return idPaciente;
    }

    public String getDniPaciente() {
        return dniPaciente;
    }

    public String getNombrePaciente() {
        return nombrePaciente;
    }

    public String getCorreoPaciente() {
        return correoPaciente;
    }

    public String getTelefonoPaciente() {
        return telefonoPaciente;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public String getNombreMedico() {
        return nombreMedico;
    }

    public Long getIdSlot() {
        return idSlot;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public AppointmentType getTipo() {
        return tipo;
    }

    public AppointmentStatus getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
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
        return o instanceof Appointment other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Appointment[id=" + id + ", " + fechaHora + ", estado=" + estado + "]";
    }
}
