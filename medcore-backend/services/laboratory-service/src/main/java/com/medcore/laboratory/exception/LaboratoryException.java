package com.medcore.laboratory.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Errores de negocio del laboratorio (regla G3).
 *
 * <p>Cada factoria fija su propio codigo y su estado HTTP, de modo que el
 * frontend pueda distinguir el caso sin leer el texto del mensaje.
 */
public class LaboratoryException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LaboratoryException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    // ---- Muestras ----------------------------------------------------------

    public static LaboratoryException muestraNoEncontrada(Long id) {
        return new LaboratoryException("MUESTRA_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe la muestra " + id + ".");
    }

    public static LaboratoryException muestraNoEncontradaPorCodigo(String codigo) {
        return new LaboratoryException("MUESTRA_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe ninguna muestra con el codigo " + codigo + ".");
    }

    /**
     * Ya hay un tubo vivo para esa orden. Para registrar otro hay que
     * rechazar el anterior indicando el motivo.
     */
    public static LaboratoryException ordenYaTieneMuestra(Long idOrden) {
        return new LaboratoryException("ORDEN_YA_TIENE_MUESTRA", HttpStatus.CONFLICT,
                "La orden " + idOrden + " ya tiene una muestra registrada. "
                        + "Rechace la anterior antes de registrar otra.");
    }

    public static LaboratoryException ordenNoEncontrada(Long idOrden) {
        return new LaboratoryException("ORDEN_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No hay ninguna orden pendiente con el identificador " + idOrden + ". "
                        + "Compruebe que medical-service la haya emitido.");
    }

    public static LaboratoryException transicionInvalida(String desde, String hacia) {
        return new LaboratoryException("TRANSICION_INVALIDA", HttpStatus.CONFLICT,
                "Una muestra " + desde + " no puede pasar a " + hacia + ".");
    }

    /**
     * Solo una muestra EN_PROCESO admite resultados: antes no se ha montado
     * en el equipo, y despues ya se cerro.
     */
    public static LaboratoryException muestraNoAdmiteResultados(String codigo, String estado) {
        return new LaboratoryException("MUESTRA_NO_ADMITE_RESULTADOS", HttpStatus.CONFLICT,
                "La muestra " + codigo + " esta " + estado
                        + " y solo se pueden registrar resultados mientras esta EN_PROCESO.");
    }

    /**
     * La muestra no esta en condiciones de tener informe.
     *
     * <p>Una muestra EN_PROCESO tiene resultados a medias, y un informe con la
     * mitad de las pruebas parece completo: quien lo lea no puede saber que
     * faltan valores. Una RECHAZADA no tiene informe, tiene motivo de rechazo.
     */
    public static LaboratoryException muestraSinInforme(String codigo, String estado) {
        return new LaboratoryException("MUESTRA_SIN_INFORME", HttpStatus.CONFLICT,
                "La muestra " + codigo + " esta " + estado
                        + " y el informe solo se emite de una muestra PROCESADA.");
    }

    public static LaboratoryException motivoRechazoRequerido() {
        return new LaboratoryException("MOTIVO_RECHAZO_REQUERIDO", HttpStatus.BAD_REQUEST,
                "Indique por que se rechaza la muestra: el medico necesita "
                        + "saberlo para repetir la toma.");
    }

    // ---- Resultados --------------------------------------------------------

    public static LaboratoryException resultadoNoEncontrado(Long id) {
        return new LaboratoryException("RESULTADO_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe el resultado " + id + ".");
    }

    public static LaboratoryException resultadoDuplicado(String codigoPrueba, String codigoMuestra) {
        return new LaboratoryException("RESULTADO_DUPLICADO", HttpStatus.CONFLICT,
                "La prueba " + codigoPrueba + " ya tiene resultado en la muestra "
                        + codigoMuestra + ". Registre una correccion si el valor cambia.");
    }

    public static LaboratoryException valorRequerido() {
        return new LaboratoryException("VALOR_REQUERIDO", HttpStatus.BAD_REQUEST,
                "Indique un valor numerico o un valor de texto para el resultado.");
    }

    /**
     * Una prueba cuantitativa necesita un numero: sin el no se puede comparar
     * con el rango de referencia ni detectar un valor critico.
     */
    public static LaboratoryException valorNumericoRequerido(String codigoPrueba) {
        return new LaboratoryException("VALOR_NUMERICO_REQUERIDO", HttpStatus.BAD_REQUEST,
                "La prueba " + codigoPrueba + " es cuantitativa y requiere un valor numerico.");
    }

    // ---- Catalogo ----------------------------------------------------------

    public static LaboratoryException pruebaNoEncontrada(String codigo) {
        return new LaboratoryException("PRUEBA_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "El codigo de prueba " + codigo + " no esta en el catalogo.");
    }

    public static LaboratoryException pruebaInactiva(String codigo) {
        return new LaboratoryException("PRUEBA_INACTIVA", HttpStatus.CONFLICT,
                "La prueba " + codigo + " esta dada de baja en el catalogo.");
    }
}
