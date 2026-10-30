package com.medcore.analytics.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Errores de negocio de analytics (regla G3).
 *
 * <p>Cada factoria fija su propio codigo y su estado HTTP, de modo que el
 * frontend pueda distinguir el caso sin leer el texto del mensaje.
 */
public class AnalyticsException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AnalyticsException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    public static AnalyticsException alertaNoEncontrada(String id) {
        return new AnalyticsException("ALERTA_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe la alerta " + id + ".");
    }

    /**
     * Una alerta ya cerrada no vuelve a abrirse. Si el brote reaparece se abre
     * otra, y el indice unico parcial de la coleccion lo permite.
     */
    public static AnalyticsException transicionInvalida(String desde, String hacia) {
        return new AnalyticsException("TRANSICION_INVALIDA", HttpStatus.CONFLICT,
                "Una alerta " + desde + " no puede pasar a " + hacia
                        + ". Si el brote reaparece, se abrira una alerta nueva.");
    }

    public static AnalyticsException motivoResolucionRequerido() {
        return new AnalyticsException("MOTIVO_REQUERIDO", HttpStatus.BAD_REQUEST,
                "Indique por que se cierra la alerta: sin el motivo no se puede "
                        + "revisar despues si el umbral estaba bien puesto.");
    }

    public static AnalyticsException rangoInvalido() {
        return new AnalyticsException("RANGO_INVALIDO", HttpStatus.BAD_REQUEST,
                "La fecha de inicio no puede ser posterior a la de fin.");
    }

    /**
     * Un rango demasiado largo haria que la agregacion recorriera millones de
     * documentos y dejaria el panel colgado. Se rechaza en lugar de servirlo
     * lento.
     */
    public static AnalyticsException rangoDemasiadoAmplio(int diasMaximos) {
        return new AnalyticsException("RANGO_DEMASIADO_AMPLIO", HttpStatus.BAD_REQUEST,
                "El rango no puede pasar de " + diasMaximos + " dias. "
                        + "Para periodos mayores, consulte los indicadores archivados.");
    }

    public static AnalyticsException indicadorNoEncontrado(String indicador) {
        return new AnalyticsException("INDICADOR_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No hay ninguna foto archivada del indicador " + indicador + ".");
    }
}
