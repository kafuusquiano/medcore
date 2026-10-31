package com.medcore.medical.domain.entity;

import com.medcore.medical.domain.enums.AttentionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Atencion medica: la consulta en la que el medico atiende al paciente.
 *
 * <p>Es el contenedor de los diagnosticos y las ordenes de laboratorio que
 * salen de esa consulta.
 *
 * <p>Una vez finalizada no se modifica: es un registro clinico. Corregirla
 * consiste en anularla y registrar otra, de modo que quede constancia de la
 * correccion.
 */
@Entity
@Table(name = "medical_attentions")
public class MedicalAttention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cita que origino la atencion; nulo si el paciente llego por urgencia. */
    @Column(name = "id_cita")
    private Long idCita;

    @Column(name = "id_paciente", nullable = false)
    private Long idPaciente;

    @Column(name = "dni_paciente", nullable = false, length = 8)
    private String dniPaciente;

    @Column(name = "nombre_paciente", nullable = false, length = 170)
    private String nombrePaciente;

    @Column(name = "correo_paciente", length = 120)
    private String correoPaciente;

    @Column(name = "id_medico", nullable = false)
    private Long idMedico;

    /**
     * Zona, edad y sexo del paciente el dia de la atencion.
     *
     * <p>Son una foto, igual que el dni y el nombre de arriba, y por el mismo
     * motivo reforzado: la vigilancia epidemiologica agrupa por zona y por
     * grupo de edad, y un caso tiene que seguir contando donde y cuando
     * ocurrio. Si el paciente se muda o cumple anios, el informe del anio
     * pasado no debe cambiar.
     *
     * <p>Nulos en las atenciones registradas antes de la migracion V4, y
     * cuando el dato no se conoce: inventarlos repartiria casos a ciegas entre
     * zonas y grupos de edad.
     */
    @Column(name = "zona_paciente", length = 80)
    private String zonaPaciente;

    @Column(name = "edad_paciente")
    private Integer edadPaciente;

    @Column(name = "sexo_paciente", length = 10)
    private String sexoPaciente;

    @Column(name = "nombre_medico", nullable = false, length = 120)
    private String nombreMedico;

    @Column(name = "motivo_consulta", nullable = false, length = 500)
    private String motivoConsulta;

    /** Lo que relata el paciente. */
    @Column(name = "anamnesis", length = 2000)
    private String anamnesis;

    /** Lo que observa el medico. */
    @Column(name = "examen_fisico", length = 2000)
    private String examenFisico;

    @Column(name = "tratamiento", length = 2000)
    private String tratamiento;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    // ---- Signos vitales ----

    @Column(name = "temperatura")
    private Double temperatura;

    @Column(name = "presion_sistolica")
    private Integer presionSistolica;

    @Column(name = "presion_diastolica")
    private Integer presionDiastolica;

    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;

    @Column(name = "peso_kg")
    private Double pesoKg;

    @Column(name = "talla_cm")
    private Double tallaCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private AttentionStatus estado = AttentionStatus.EN_CURSO;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "fecha_atencion", nullable = false, updatable = false)
    private Instant fechaAtencion;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Diagnosticos de esta atencion.
     *
     * <p>Cascada ALL con orphanRemoval: los diagnosticos no tienen sentido
     * fuera de su atencion y no se consultan por separado.
     */
    @OneToMany(mappedBy = "atencion", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Diagnosis> diagnosticos = new ArrayList<>();

    protected MedicalAttention() {
        // Requerido por JPA.
    }

    public MedicalAttention(Long idCita, Long idPaciente, String dniPaciente,
                            String nombrePaciente, String correoPaciente,
                            String zonaPaciente, Integer edadPaciente,
                            String sexoPaciente, Long idMedico,
                            String nombreMedico, String motivoConsulta) {
        this.idCita = idCita;
        this.idPaciente = idPaciente;
        this.dniPaciente = dniPaciente;
        this.nombrePaciente = nombrePaciente;
        this.correoPaciente = correoPaciente;
        this.zonaPaciente = zonaPaciente;
        this.edadPaciente = edadPaciente;
        this.sexoPaciente = sexoPaciente;
        this.idMedico = idMedico;
        this.nombreMedico = nombreMedico;
        this.motivoConsulta = motivoConsulta;
        this.estado = AttentionStatus.EN_CURSO;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.fechaAtencion = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    // ---- Comportamiento de dominio -----------------------------------------

    private void exigirAbierta() {
        if (!estado.admiteCambios()) {
            throw new IllegalStateException(
                    "La atencion esta " + estado + " y no admite cambios");
        }
    }

    public void registrarHallazgos(String anamnesis, String examenFisico,
                                   String tratamiento, String observaciones) {
        exigirAbierta();
        this.anamnesis = anamnesis;
        this.examenFisico = examenFisico;
        this.tratamiento = tratamiento;
        this.observaciones = observaciones;
    }

    public void registrarSignosVitales(Double temperatura, Integer sistolica, Integer diastolica,
                                       Integer frecuenciaCardiaca, Double pesoKg, Double tallaCm) {
        exigirAbierta();
        this.temperatura = temperatura;
        this.presionSistolica = sistolica;
        this.presionDiastolica = diastolica;
        this.frecuenciaCardiaca = frecuenciaCardiaca;
        this.pesoKg = pesoKg;
        this.tallaCm = tallaCm;
    }

    public void agregarDiagnostico(Diagnosis diagnostico) {
        exigirAbierta();
        diagnostico.asignarA(this);
        this.diagnosticos.add(diagnostico);
    }

    /**
     * Cierra la atencion.
     *
     * @throws IllegalStateException si no tiene ningun diagnostico. Una
     *         consulta sin diagnostico no deja registro de por que se atendio
     *         al paciente ni sirve para la estadistica epidemiologica.
     */
    public void finalizar() {
        // Se valida ANTES de cambiar el estado: si se hiciera al reves y la
        // excepcion se capturara, el objeto quedaria marcado como finalizado
        // en memoria sin haberlo sido.
        if (diagnosticos.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede finalizar una atencion sin al menos un diagnostico");
        }
        cambiarEstado(AttentionStatus.FINALIZADA);
        this.fechaCierre = Instant.now();
    }

    public void anular(String motivo) {
        cambiarEstado(AttentionStatus.ANULADA);
        this.motivoAnulacion = motivo;
        this.fechaCierre = Instant.now();
    }

    private void cambiarEstado(AttentionStatus destino) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("No se puede pasar de " + estado + " a " + destino);
        }
        this.estado = destino;
    }

    /** Indice de masa corporal, si hay peso y talla. */
    public Double imc() {
        if (pesoKg == null || tallaCm == null || tallaCm <= 0) {
            return null;
        }
        double metros = tallaCm / 100.0;
        return Math.round((pesoKg / (metros * metros)) * 100.0) / 100.0;
    }

    /** Diagnostico principal: el primero que registro el medico. */
    public Diagnosis diagnosticoPrincipal() {
        return diagnosticos.isEmpty() ? null : diagnosticos.get(0);
    }

    // ---- Accesores ---------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getIdCita() {
        return idCita;
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

    public Long getIdMedico() {
        return idMedico;
    }

    public String getZonaPaciente() {
        return zonaPaciente;
    }

    public Integer getEdadPaciente() {
        return edadPaciente;
    }

    public String getSexoPaciente() {
        return sexoPaciente;
    }

    public String getNombreMedico() {
        return nombreMedico;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public String getAnamnesis() {
        return anamnesis;
    }

    public String getExamenFisico() {
        return examenFisico;
    }

    public String getTratamiento() {
        return tratamiento;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Double getTemperatura() {
        return temperatura;
    }

    public Integer getPresionSistolica() {
        return presionSistolica;
    }

    public Integer getPresionDiastolica() {
        return presionDiastolica;
    }

    public Integer getFrecuenciaCardiaca() {
        return frecuenciaCardiaca;
    }

    public Double getPesoKg() {
        return pesoKg;
    }

    public Double getTallaCm() {
        return tallaCm;
    }

    public AttentionStatus getEstado() {
        return estado;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public Instant getFechaAtencion() {
        return fechaAtencion;
    }

    public Instant getFechaCierre() {
        return fechaCierre;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Lista inmutable: se anaden diagnosticos con agregarDiagnostico. */
    public List<Diagnosis> getDiagnosticos() {
        return Collections.unmodifiableList(diagnosticos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof MedicalAttention other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "MedicalAttention[id=" + id + ", paciente=" + idPaciente + ", estado=" + estado + "]";
    }
}
