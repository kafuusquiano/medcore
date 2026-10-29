package com.medcore.appointment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Horario de atencion de un medico.
 *
 * <p>Describe en que dias de la semana atiende y entre que horas. De aqui
 * sale la generacion de slots: el horario es la plantilla y los slots son las
 * franjas concretas de cada fecha.
 */
@Entity
@Table(name = "doctor_schedule")
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id del usuario con rol MEDICO en auth_db. No hay FK: otra base (MC1). */
    @Column(name = "id_medico", nullable = false)
    private Long idMedico;

    @Column(name = "nombre_medico", nullable = false, length = 120)
    private String nombreMedico;

    @Column(name = "especialidad", length = 80)
    private String especialidad;

    /**
     * Dias en que atiende, como lista separada por comas de nombres de
     * {@link DayOfWeek}: "MONDAY,TUESDAY,FRIDAY".
     *
     * <p>Se guarda asi, y no en una tabla aparte, porque siempre se lee
     * entero junto al horario y nunca se consulta por dia suelto.
     */
    @Column(name = "dias_disponibles", nullable = false, length = 120)
    private String diasDisponibles;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected DoctorSchedule() {
        // Requerido por JPA.
    }

    public DoctorSchedule(Long idMedico, String nombreMedico, String especialidad,
                          Set<DayOfWeek> dias, LocalTime horaInicio, LocalTime horaFin) {
        this.idMedico = idMedico;
        this.nombreMedico = nombreMedico;
        this.especialidad = especialidad;
        this.diasDisponibles = serializar(dias);
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.activo = true;
    }

    public void actualizar(String nombreMedico, String especialidad, Set<DayOfWeek> dias,
                           LocalTime horaInicio, LocalTime horaFin) {
        this.nombreMedico = nombreMedico;
        this.especialidad = especialidad;
        this.diasDisponibles = serializar(dias);
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    /** Dias de atencion, en orden de la semana. */
    public Set<DayOfWeek> dias() {
        return Arrays.stream(diasDisponibles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean atiendeEl(DayOfWeek dia) {
        return activo && dias().contains(dia);
    }

    private static String serializar(Set<DayOfWeek> dias) {
        return dias.stream()
                .sorted()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    public Long getId() {
        return id;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public String getNombreMedico() {
        return nombreMedico;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public String getDiasDisponibles() {
        return diasDisponibles;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public boolean isActivo() {
        return activo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof DoctorSchedule other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DoctorSchedule[id=" + id + ", idMedico=" + idMedico + "]";
    }
}
