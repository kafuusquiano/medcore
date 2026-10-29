package com.medcore.appointment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Franja concreta de la agenda de un medico, de 30 minutos por defecto.
 *
 * <p>Es el recurso que se disputa al reservar. La doble reserva se evita
 * combinando dos defensas:
 *
 * <ul>
 *   <li>Un indice unico sobre (id_medico, fecha, hora_inicio) en la base, que
 *       impide que existan dos franjas iguales.</li>
 *   <li>Un bloqueo pesimista al reservar
 *       ({@code SELECT ... FOR UPDATE} desde el repositorio), que serializa a
 *       dos peticiones simultaneas sobre el mismo slot. El bloqueo optimista
 *       de {@code @Version} por si solo no basta: la segunda peticion
 *       fallaria con un error de concurrencia en lugar de ver el slot ya
 *       ocupado y responder un conflicto claro.</li>
 * </ul>
 */
@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_medico", nullable = false)
    private Long idMedico;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "ocupado", nullable = false)
    private boolean ocupado = false;

    /** Cita que lo ocupa; nulo mientras esta libre. */
    @Column(name = "id_cita")
    private Long idCita;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TimeSlot() {
        // Requerido por JPA.
    }

    public TimeSlot(Long idMedico, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        this.idMedico = idMedico;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.ocupado = false;
    }

    /**
     * Marca el slot como ocupado por una cita.
     *
     * @throws IllegalStateException si ya estaba ocupado. Quien llama debe
     *         haberlo leido con bloqueo pesimista; esta comprobacion es la
     *         ultima red por si no se hizo.
     */
    public void reservar(Long idCita) {
        if (this.ocupado) {
            throw new IllegalStateException("El horario ya esta ocupado");
        }
        this.ocupado = true;
        this.idCita = idCita;
    }

    public void liberar() {
        this.ocupado = false;
        this.idCita = null;
    }

    public boolean estaLibre() {
        return !ocupado;
    }

    public LocalDateTime inicio() {
        return LocalDateTime.of(fecha, horaInicio);
    }

    /** Un slot ya pasado no se puede reservar aunque figure libre. */
    public boolean yaPaso() {
        return inicio().isBefore(LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public boolean isOcupado() {
        return ocupado;
    }

    public Long getIdCita() {
        return idCita;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof TimeSlot other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "TimeSlot[id=" + id + ", medico=" + idMedico + ", " + fecha + " " + horaInicio
                + ", ocupado=" + ocupado + "]";
    }
}
