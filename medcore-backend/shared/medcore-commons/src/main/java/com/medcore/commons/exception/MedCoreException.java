package com.medcore.commons.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Map;

/**
 * Excepcion base del dominio de MedCore.
 *
 * <p>Cada servicio define su propia subclase ({@code PatientException},
 * {@code AuthException}, etc.) y el handler global la traduce al codigo HTTP
 * que indica {@link #getEstado()}, sin exponer el stack trace (regla G3).
 */
public class MedCoreException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Identificador estable del error, por ejemplo PACIENTE_NO_ENCONTRADO. */
    private final String codigo;

    /** Codigo HTTP con el que debe responderse al cliente. */
    private final HttpStatus estado;

    /** Informacion adicional del dominio; puede ir vacia. */
    private final transient Map<String, Object> detalles;

    public MedCoreException(String codigo, HttpStatus estado, String mensaje) {
        this(codigo, estado, mensaje, Map.of(), null);
    }

    public MedCoreException(String codigo, HttpStatus estado, String mensaje,
                            Map<String, Object> detalles) {
        this(codigo, estado, mensaje, detalles, null);
    }

    public MedCoreException(String codigo, HttpStatus estado, String mensaje,
                            Map<String, Object> detalles, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
        this.estado = estado;
        this.detalles = detalles == null ? Map.of() : Map.copyOf(detalles);
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public Map<String, Object> getDetalles() {
        return detalles;
    }

    // ---- Atajos para los casos mas frecuentes -------------------------------

    public static MedCoreException noEncontrado(String codigo, String mensaje) {
        return new MedCoreException(codigo, HttpStatus.NOT_FOUND, mensaje);
    }

    public static MedCoreException conflicto(String codigo, String mensaje) {
        return new MedCoreException(codigo, HttpStatus.CONFLICT, mensaje);
    }

    public static MedCoreException peticionInvalida(String codigo, String mensaje) {
        return new MedCoreException(codigo, HttpStatus.BAD_REQUEST, mensaje);
    }

    public static MedCoreException noAutorizado(String codigo, String mensaje) {
        return new MedCoreException(codigo, HttpStatus.UNAUTHORIZED, mensaje);
    }

    public static MedCoreException prohibido(String codigo, String mensaje) {
        return new MedCoreException(codigo, HttpStatus.FORBIDDEN, mensaje);
    }

    public static MedCoreException noProcesable(String codigo, String mensaje) {
        return new MedCoreException(codigo, HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
