package com.medcore.medical.domain.entity;

import com.medcore.medical.domain.enums.LabOrderPriority;
import com.medcore.medical.domain.enums.LabOrderStatus;
import jakarta.persistence.CascadeType;
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
 * Orden de laboratorio emitida en una atencion.
 *
 * <p>Es el punto de union entre medical-service y laboratory-service: al
 * crearse se publica {@code lab-order.created}, y aquel abre la orden y
 * espera las muestras.
 *
 * <p>El estado que se guarda aqui es un reflejo del avance real, que ocurre
 * en laboratory-service. La unica transicion que decide este servicio es la
 * anulacion, y solo mientras la muestra no se haya tomado.
 */
@Entity
@Table(name = "lab_orders")
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Codigo legible que se imprime en la orden y lleva el paciente al
     * laboratorio, por ejemplo ORD-20261029-0007. El id numerico no sirve
     * para eso: es un detalle interno de la base.
     */
    @Column(name = "codigo", nullable = false, unique = true, length = 25)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attention_id", nullable = false)
    private MedicalAttention atencion;

    @Column(name = "id_paciente", nullable = false)
    private Long idPaciente;

    @Column(name = "id_medico", nullable = false)
    private Long idMedico;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", nullable = false, length = 15)
    private LabOrderPriority prioridad = LabOrderPriority.RUTINA;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private LabOrderStatus estado = LabOrderStatus.EMITIDA;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private Instant fechaEmision;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LabOrderTest> pruebas = new ArrayList<>();

    protected LabOrder() {
        // Requerido por JPA.
    }

    public LabOrder(String codigo, MedicalAttention atencion, LabOrderPriority prioridad,
                    String observaciones) {
        this.codigo = codigo;
        this.atencion = atencion;
        this.idPaciente = atencion.getIdPaciente();
        this.idMedico = atencion.getIdMedico();
        this.prioridad = prioridad == null ? LabOrderPriority.RUTINA : prioridad;
        this.observaciones = observaciones;
        this.estado = LabOrderStatus.EMITIDA;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.fechaEmision = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    public void agregarPrueba(LabOrderTest prueba) {
        if (estado != LabOrderStatus.EMITIDA) {
            throw new IllegalStateException(
                    "No se pueden anadir pruebas a una orden " + estado);
        }
        prueba.asignarA(this);
        this.pruebas.add(prueba);
    }

    public void marcarEnProceso() {
        cambiarEstado(LabOrderStatus.EN_PROCESO);
    }

    public void marcarCompletada() {
        cambiarEstado(LabOrderStatus.COMPLETADA);
    }

    /**
     * Anula la orden.
     *
     * @throws IllegalStateException si el laboratorio ya empezo. Despues de
     *         tomar la muestra no se anula: ya se pincho al paciente y existe
     *         material en proceso.
     */
    public void anular(String motivo) {
        if (!estado.admiteAnulacion()) {
            throw new IllegalStateException(
                    "Una orden " + estado + " ya no puede anularse: el laboratorio comenzo el proceso");
        }
        cambiarEstado(LabOrderStatus.ANULADA);
        this.motivoAnulacion = motivo;
    }

    private void cambiarEstado(LabOrderStatus destino) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("No se puede pasar de " + estado + " a " + destino);
        }
        this.estado = destino;
    }

    /** Codigos de las pruebas, que es lo que viaja en el evento. */
    public List<String> codigosDePruebas() {
        return pruebas.stream().map(LabOrderTest::getCodigoPrueba).toList();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public MedicalAttention getAtencion() {
        return atencion;
    }

    public Long getIdPaciente() {
        return idPaciente;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public LabOrderPriority getPrioridad() {
        return prioridad;
    }

    public LabOrderStatus getEstado() {
        return estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public Instant getFechaEmision() {
        return fechaEmision;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public List<LabOrderTest> getPruebas() {
        return Collections.unmodifiableList(pruebas);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof LabOrder other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LabOrder[" + codigo + ", estado=" + estado + "]";
    }
}
