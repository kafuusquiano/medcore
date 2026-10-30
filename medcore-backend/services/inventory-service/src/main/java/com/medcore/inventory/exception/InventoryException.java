package com.medcore.inventory.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Errores de negocio del inventario (regla G3).
 *
 * <p>Cada factoria fija su propio codigo y su estado HTTP, de modo que el
 * frontend pueda distinguir el caso sin leer el texto del mensaje.
 */
public class InventoryException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    // ---- Reactivos ---------------------------------------------------------

    public static InventoryException reactivoNoEncontrado(Long id) {
        return new InventoryException("REACTIVO_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe el reactivo " + id + ".");
    }

    public static InventoryException reactivoNoEncontradoPorCodigo(String codigo) {
        return new InventoryException("REACTIVO_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe ningun reactivo con el codigo " + codigo + ".");
    }

    public static InventoryException codigoDuplicado(String codigo) {
        return new InventoryException("CODIGO_DUPLICADO", HttpStatus.CONFLICT,
                "Ya existe un reactivo con el codigo " + codigo + ".");
    }

    public static InventoryException reactivoInactivo(String codigo) {
        return new InventoryException("REACTIVO_INACTIVO", HttpStatus.CONFLICT,
                "El reactivo " + codigo + " esta dado de baja. Reactivelo antes de "
                        + "registrar material.");
    }

    // ---- Lotes -------------------------------------------------------------

    public static InventoryException loteNoEncontrado(Long id) {
        return new InventoryException("LOTE_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe el lote " + id + ".");
    }

    public static InventoryException loteDuplicado(String lote, String codigoReactivo) {
        return new InventoryException("LOTE_DUPLICADO", HttpStatus.CONFLICT,
                "El lote " + lote + " ya esta registrado para el reactivo " + codigoReactivo
                        + ". Registre una entrada sobre el lote existente.");
    }

    public static InventoryException loteRetirado(String lote) {
        return new InventoryException("LOTE_RETIRADO", HttpStatus.CONFLICT,
                "El lote " + lote + " esta retirado del almacen y no admite movimientos.");
    }

    /**
     * Un lote vencido no se consume aunque queden unidades: usarlo daria un
     * resultado clinico que no se puede respaldar. Si hay que sacarlo de la
     * estanteria, el movimiento correcto es una MERMA.
     */
    public static InventoryException loteVencido(String lote, String fechaVencimiento) {
        return new InventoryException("LOTE_VENCIDO", HttpStatus.CONFLICT,
                "El lote " + lote + " vencio el " + fechaVencimiento
                        + " y no se puede consumir. Registrelo como merma para retirarlo.");
    }

    public static InventoryException vencimientoAnteriorAlIngreso() {
        return new InventoryException("VENCIMIENTO_INVALIDO", HttpStatus.BAD_REQUEST,
                "La fecha de vencimiento no puede ser anterior a la de ingreso.");
    }

    // ---- Movimientos -------------------------------------------------------

    public static InventoryException stockInsuficiente(String lote, int disponible, int solicitado) {
        return new InventoryException("STOCK_INSUFICIENTE", HttpStatus.CONFLICT,
                "El lote " + lote + " solo tiene " + disponible + " unidades y se solicitan "
                        + solicitado + ".");
    }

    public static InventoryException cantidadInvalida() {
        return new InventoryException("CANTIDAD_INVALIDA", HttpStatus.BAD_REQUEST,
                "La cantidad debe ser distinta de cero.");
    }

    /**
     * Solo el AJUSTE corrige en cualquier direccion. En el resto, el signo lo
     * pone el tipo de movimiento, de modo que enviar una cantidad negativa
     * indica que quien llama entendio mal la operacion.
     */
    public static InventoryException cantidadNegativaNoPermitida(String tipo) {
        return new InventoryException("CANTIDAD_NEGATIVA_NO_PERMITIDA", HttpStatus.BAD_REQUEST,
                "Un movimiento de tipo " + tipo + " no admite cantidad negativa: el signo lo "
                        + "determina el tipo. Use AJUSTE para corregir a la baja.");
    }

    public static InventoryException motivoRequerido(String tipo) {
        return new InventoryException("MOTIVO_REQUERIDO", HttpStatus.BAD_REQUEST,
                "Un movimiento de tipo " + tipo + " requiere indicar el motivo.");
    }

    public static InventoryException sinStockDisponible(String codigoReactivo, int solicitado) {
        return new InventoryException("SIN_STOCK_DISPONIBLE", HttpStatus.CONFLICT,
                "No hay " + solicitado + " unidades disponibles de " + codigoReactivo
                        + " en lotes vigentes.");
    }
}
