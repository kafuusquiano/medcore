package com.medcore.laboratory.domain.entity;

import com.medcore.laboratory.domain.enums.SampleStatus;
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
import java.util.Objects;

/**
 * Muestra tomada al paciente para una orden de laboratorio.
 *
 * <p>Guarda una copia de los datos del paciente que llegaron en el evento
 * {@code lab-order.created}. Es duplicacion deliberada: el laboratorio debe
 * poder rotular, procesar y emitir el resultado sin depender de que
 * patient-service responda, y el resultado debe conservar a nombre de quien
 * se emitio.
 *
 * <p>La zona se guarda porque analytics-service agrupa los casos por zona
 * para detectar brotes, y recibe esa informacion en el evento del resultado.
 */
@Entity
@Table(name = "samples")
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Codigo que se imprime en la etiqueta del tubo, por ejemplo
     * MUE-20261029-0012. Es lo que lee el equipo y lo que busca el personal.
     */
    @Column(name = "codigo", nullable = false, unique = true, length = 25)
    private String codigo;

    @Column(name = "id_orden", nullable = false)
    private Long idOrden;

    @Column(name = "codigo_orden", nullable = false, length = 25)
    private String codigoOrden;

    @Column(name = "id_paciente", nullable = false)
    private Long idPaciente;

    @Column(name = "dni_paciente", nullable = false, length = 8)
    private String dniPaciente;

    @Column(name = "nombre_paciente", nullable = false, length = 170)
    private String nombrePaciente;

    @Column(name = "correo_paciente", length = 120)
    private String correoPaciente;

    /** MASCULINO o FEMENINO; determina que rango de referencia aplica. */
    @Column(name = "sexo_paciente", length = 20)
    private String sexoPaciente;

    /** Edad al tomar la muestra; tambien selecciona el rango. */
    @Column(name = "edad_paciente")
    private Integer edadPaciente;

    /** Distrito del paciente; analytics-service agrupa los brotes por zona. */
    @Column(name = "zona_paciente", length = 80)
    private String zonaPaciente;

    @Column(name = "id_medico", nullable = false)
    private Long idMedico;

    @Column(name = "tipo_muestra", nullable = false, length = 30)
    private String tipoMuestra;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private SampleStatus estado = SampleStatus.REGISTRADA;

    @Column(name = "urgente", nullable = false)
    private boolean urgente = false;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "motivo_rechazo", length = 300)
    private String motivoRechazo;

    @Column(name = "tomada_por", length = 120)
    private String tomadaPor;

    @Column(name = "fecha_toma", nullable = false, updatable = false)
    private Instant fechaToma;

    @Column(name = "fecha_proceso")
    private Instant fechaProceso;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Sample() {
        // Requerido por JPA.
    }

    public Sample(String codigo, Long idOrden, String codigoOrden, Long idPaciente,
                  String dniPaciente, String nombrePaciente, String correoPaciente,
                  String sexoPaciente, Integer edadPaciente, String zonaPaciente,
                  Long idMedico, String tipoMuestra, boolean urgente, String tomadaPor) {
        this.codigo = codigo;
        this.idOrden = idOrden;
        this.codigoOrden = codigoOrden;
        this.idPaciente = idPaciente;
        this.dniPaciente = dniPaciente;
        this.nombrePaciente = nombrePaciente;
        this.correoPaciente = correoPaciente;
        this.sexoPaciente = sexoPaciente;
        this.edadPaciente = edadPaciente;
        this.zonaPaciente = zonaPaciente;
        this.idMedico = idMedico;
        this.tipoMuestra = tipoMuestra;
        this.urgente = urgente;
        this.tomadaPor = tomadaPor;
        this.estado = SampleStatus.REGISTRADA;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.fechaToma = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    // ---- Comportamiento de dominio -----------------------------------------

    public void iniciarProceso() {
        cambiarEstado(SampleStatus.EN_PROCESO);
        this.fechaProceso = Instant.now();
    }

    public void marcarProcesada() {
        cambiarEstado(SampleStatus.PROCESADA);
    }

    /**
     * Rechaza la muestra.
     *
     * <p>Es un desenlace normal del laboratorio: llega hemolizada, mal
     * rotulada o con volumen insuficiente. Queda registrado con su motivo
     * para que el medico sepa que debe repetirse la toma.
     */
    public void rechazar(String motivo) {
        cambiarEstado(SampleStatus.RECHAZADA);
        this.motivoRechazo = motivo;
    }

    private void cambiarEstado(SampleStatus destino) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("No se puede pasar de " + estado + " a " + destino);
        }
        this.estado = destino;
    }

    public void anotar(String observaciones) {
        this.observaciones = observaciones;
    }

    public boolean admiteResultados() {
        return estado.admiteResultados();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getIdOrden() {
        return idOrden;
    }

    public String getCodigoOrden() {
        return codigoOrden;
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

    public String getSexoPaciente() {
        return sexoPaciente;
    }

    public Integer getEdadPaciente() {
        return edadPaciente;
    }

    public String getZonaPaciente() {
        return zonaPaciente;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public String getTipoMuestra() {
        return tipoMuestra;
    }

    public SampleStatus getEstado() {
        return estado;
    }

    public boolean isUrgente() {
        return urgente;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public String getTomadaPor() {
        return tomadaPor;
    }

    public Instant getFechaToma() {
        return fechaToma;
    }

    public Instant getFechaProceso() {
        return fechaProceso;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Sample other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Sample[" + codigo + ", estado=" + estado + "]";
    }
}
