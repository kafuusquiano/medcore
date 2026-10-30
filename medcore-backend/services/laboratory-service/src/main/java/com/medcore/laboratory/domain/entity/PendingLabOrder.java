package com.medcore.laboratory.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orden de laboratorio en espera de que llegue el tubo.
 *
 * <p>Es una proyeccion local del evento {@code lab-order.created}. El dueno
 * de la orden es medical-service (MC1): aqui no se modifica su contenido, solo
 * se marca como atendida cuando se registra la muestra correspondiente.
 *
 * <p>La clave primaria es el identificador que trae el evento y no uno
 * generado. Asi, si Kafka entrega el mismo evento dos veces, que es algo que
 * su garantia "al menos una vez" permite, el segundo intento choca con la
 * clave existente en lugar de crear una orden duplicada en la cola.
 */
@Entity
@Table(name = "pending_lab_orders")
public class PendingLabOrder {

    @Id
    @Column(name = "id_orden", nullable = false, updatable = false)
    private Long idOrden;

    @Column(name = "codigo_orden", nullable = false, length = 25)
    private String codigoOrden;

    @Column(name = "id_atencion")
    private Long idAtencion;

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

    @Column(name = "nombre_medico", length = 170)
    private String nombreMedico;

    @Column(nullable = false, length = 20)
    private String prioridad = "RUTINA";

    @Column(length = 500)
    private String observaciones;

    /**
     * Pasa a true al registrarse la muestra. La fila no se borra: deja
     * constancia de cuanto tardo el tubo en llegar y de las ordenes que
     * nunca se atendieron.
     */
    @Column(nullable = false)
    private boolean atendida = false;

    /**
     * Los codigos de prueba se guardan como coleccion de valores y no como
     * entidad propia: no tienen comportamiento ni identidad fuera de su
     * orden, y nunca se consultan por separado.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "pending_lab_order_tests",
            joinColumns = @JoinColumn(name = "id_orden"))
    @Column(name = "codigo_prueba", nullable = false, length = 20)
    private List<String> pruebas = new ArrayList<>();

    @Column(name = "fecha_evento", nullable = false)
    private Instant fechaEvento;

    @Column(name = "fecha_recepcion", nullable = false)
    private Instant fechaRecepcion;

    protected PendingLabOrder() {
        // Requerido por JPA.
    }

    public PendingLabOrder(Long idOrden, String codigoOrden, Long idAtencion, Long idPaciente,
                           String dniPaciente, String nombrePaciente, String correoPaciente,
                           Long idMedico, String nombreMedico, String prioridad,
                           String observaciones, List<String> pruebas, Instant fechaEvento) {
        this.idOrden = idOrden;
        this.codigoOrden = codigoOrden;
        this.idAtencion = idAtencion;
        this.idPaciente = idPaciente;
        this.dniPaciente = dniPaciente;
        this.nombrePaciente = nombrePaciente;
        this.correoPaciente = correoPaciente;
        this.idMedico = idMedico;
        this.nombreMedico = nombreMedico;
        this.prioridad = prioridad == null ? "RUTINA" : prioridad;
        this.observaciones = observaciones;
        this.pruebas = pruebas == null ? new ArrayList<>() : new ArrayList<>(pruebas);
        this.fechaEvento = fechaEvento;
    }

    @PrePersist
    void alCrear() {
        this.fechaRecepcion = Instant.now();
        if (this.fechaEvento == null) {
            this.fechaEvento = this.fechaRecepcion;
        }
    }

    public void marcarAtendida() {
        this.atendida = true;
    }

    /**
     * Devuelve la orden a la cola tras rechazarse su muestra.
     *
     * <p>El contenido de la orden no se toca: sigue siendo la proyeccion del
     * evento que la emitio, cuyo dueno es medical-service (MC1). Lo unico que
     * cambia es que el laboratorio vuelve a esperar un tubo, porque el que
     * recibio no era apto y hay que repetir la toma. El indice unico parcial
     * de V2 permite esa segunda muestra viva.
     */
    public void reabrir() {
        this.atendida = false;
    }

    public boolean esUrgente() {
        return "URGENTE".equals(prioridad);
    }

    public Long getIdOrden() {
        return idOrden;
    }

    public String getCodigoOrden() {
        return codigoOrden;
    }

    public Long getIdAtencion() {
        return idAtencion;
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

    public String getNombreMedico() {
        return nombreMedico;
    }

    public String getPrioridad() {
        return prioridad;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public boolean isAtendida() {
        return atendida;
    }

    public List<String> getPruebas() {
        return List.copyOf(pruebas);
    }

    public Instant getFechaEvento() {
        return fechaEvento;
    }

    public Instant getFechaRecepcion() {
        return fechaRecepcion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PendingLabOrder otra)) {
            return false;
        }
        return idOrden != null && idOrden.equals(otra.idOrden);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(idOrden);
    }

    @Override
    public String toString() {
        return "PendingLabOrder{orden=" + codigoOrden + ", atendida=" + atendida + "}";
    }
}
