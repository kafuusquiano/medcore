package com.medcore.inventory.domain.entity;

import com.medcore.inventory.domain.enums.MovementType;
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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de un movimiento de stock.
 *
 * <p>Es inmutable: no tiene setters ni metodos que cambien nada despues de
 * crearlo. Un movimiento no se edita ni se borra, porque es la auditoria del
 * inventario; si se registro mal, se corrige con un AJUSTE que deja
 * constancia de la diferencia.
 *
 * <p>Guarda la cantidad antes y despues, no solo el delta. Con eso, un
 * recuento que no cuadra se puede rastrear hasta el movimiento exacto en que
 * la cuenta se desvio, sin tener que sumar toda la cadena desde el principio.
 */
@Entity
@Table(name = "movement_logs")
public class MovementLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    /**
     * Se copian el codigo y el lote, ademas de la relacion.
     *
     * <p>Es duplicacion deliberada: el historial debe poder leerse e
     * imprimirse sin cargar el lote ni el reactivo, y debe seguir siendo
     * legible si el reactivo se da de baja del catalogo.
     */
    @Column(name = "codigo_reactivo", nullable = false, length = 30)
    private String codigoReactivo;

    @Column(name = "nombre_reactivo", nullable = false, length = 150)
    private String nombreReactivo;

    @Column(nullable = false, length = 60)
    private String lote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType tipo;

    /** Delta aplicado, con signo: negativo en salidas y mermas. */
    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "cantidad_anterior", nullable = false)
    private Integer cantidadAnterior;

    @Column(name = "cantidad_resultante", nullable = false)
    private Integer cantidadResultante;

    /**
     * Por que se movio el material. Obligatorio en los tipos que no son
     * consumo normal: una merma o un ajuste sin motivo no sirven para
     * auditar nada.
     */
    @Column(length = 300)
    private String motivo;

    /** Usuario que lo registro, de la cabecera X-User-Id del gateway. */
    @Column(name = "registrado_por", nullable = false, length = 120)
    private String registradoPor;

    /**
     * Muestra que consumio el material, si el movimiento viene del
     * laboratorio. Nulo en entradas, mermas y ajustes.
     *
     * <p>Es un identificador suelto y no una clave ajena: la muestra vive en
     * lab_db, que es de otro servicio (MC1).
     */
    @Column(name = "codigo_muestra", length = 25)
    private String codigoMuestra;

    @Column(name = "fecha_movimiento", nullable = false, updatable = false)
    private Instant fechaMovimiento;

    protected MovementLog() {
        // Requerido por JPA.
    }

    public MovementLog(Stock stock, MovementType tipo, int delta, int cantidadAnterior,
                       String motivo, String registradoPor, String codigoMuestra) {
        this.stock = stock;
        this.codigoReactivo = stock.getReactivo().getCodigo();
        this.nombreReactivo = stock.getReactivo().getNombre();
        this.lote = stock.getLote();
        this.tipo = tipo;
        this.cantidad = delta;
        this.cantidadAnterior = cantidadAnterior;
        this.cantidadResultante = cantidadAnterior + delta;
        this.motivo = motivo;
        this.registradoPor = registradoPor;
        this.codigoMuestra = codigoMuestra;
    }

    @PrePersist
    void alCrear() {
        this.fechaMovimiento = Instant.now();
    }

    public boolean esEntrada() {
        return cantidad > 0;
    }

    public Long getId() {
        return id;
    }

    public Stock getStock() {
        return stock;
    }

    public String getCodigoReactivo() {
        return codigoReactivo;
    }

    public String getNombreReactivo() {
        return nombreReactivo;
    }

    public String getLote() {
        return lote;
    }

    public MovementType getTipo() {
        return tipo;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public Integer getCantidadAnterior() {
        return cantidadAnterior;
    }

    public Integer getCantidadResultante() {
        return cantidadResultante;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public String getCodigoMuestra() {
        return codigoMuestra;
    }

    public Instant getFechaMovimiento() {
        return fechaMovimiento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MovementLog otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "MovementLog{" + tipo + " " + cantidad + " de " + lote + "}";
    }
}
