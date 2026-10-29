package com.medcore.medical.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Map;

/**
 * Errores propios de medical-service.
 */
public class MedicalException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MedicalException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    public MedicalException(String codigo, HttpStatus estado, String mensaje,
                            Map<String, Object> detalles) {
        super(codigo, estado, mensaje, detalles);
    }

    public static MedicalException atencionNoEncontrada(Long id) {
        return new MedicalException("ATENCION_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe una atencion con el identificador indicado.", Map.of("id", id));
    }

    public static MedicalException ordenNoEncontrada(Long id) {
        return new MedicalException("ORDEN_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe una orden de laboratorio con el identificador indicado.",
                Map.of("id", id));
    }

    public static MedicalException ordenNoEncontradaPorCodigo(String codigo) {
        return new MedicalException("ORDEN_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe una orden con el codigo " + codigo + ".");
    }

    public static MedicalException cie10NoExiste(String codigo) {
        return new MedicalException("CIE10_NO_EXISTE", HttpStatus.UNPROCESSABLE_ENTITY,
                "El codigo CIE-10 " + codigo + " no esta en el catalogo.");
    }

    public static MedicalException diagnosticoDuplicado(String codigo) {
        return new MedicalException("DIAGNOSTICO_DUPLICADO", HttpStatus.CONFLICT,
                "El diagnostico " + codigo + " ya fue registrado en esta atencion.");
    }

    public static MedicalException yaHayDiagnosticoPrincipal() {
        return new MedicalException("DIAGNOSTICO_PRINCIPAL_DUPLICADO", HttpStatus.CONFLICT,
                "La atencion ya tiene un diagnostico principal.");
    }

    public static MedicalException citaYaAtendida(Long idCita) {
        return new MedicalException("CITA_YA_ATENDIDA", HttpStatus.CONFLICT,
                "Esa cita ya tiene una atencion registrada.", Map.of("idCita", idCita));
    }

    public static MedicalException atencionCerrada() {
        return new MedicalException("ATENCION_CERRADA", HttpStatus.CONFLICT,
                "La atencion ya fue cerrada y no admite cambios.");
    }

    public static MedicalException sinDiagnostico() {
        return new MedicalException("ATENCION_SIN_DIAGNOSTICO", HttpStatus.CONFLICT,
                "No se puede finalizar una atencion sin al menos un diagnostico.");
    }

    public static MedicalException ordenSinPruebas() {
        return new MedicalException("ORDEN_SIN_PRUEBAS", HttpStatus.BAD_REQUEST,
                "Una orden de laboratorio debe incluir al menos una prueba.");
    }

    public static MedicalException ordenNoAnulable(String estado) {
        return new MedicalException("ORDEN_NO_ANULABLE", HttpStatus.CONFLICT,
                "Una orden en estado " + estado + " ya no puede anularse: "
                        + "el laboratorio comenzo el proceso.");
    }

    public static MedicalException rangoFechasInvalido() {
        return new MedicalException("RANGO_FECHAS_INVALIDO", HttpStatus.BAD_REQUEST,
                "La fecha de inicio no puede ser posterior a la de fin.");
    }
}
