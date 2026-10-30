package com.medcore.notification.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Errores de negocio de notification-service (regla G3).
 *
 * <p>Cada factoria fija su propio codigo y su estado HTTP, de modo que el
 * frontend pueda distinguir el caso sin leer el texto del mensaje.
 */
public class NotificationException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotificationException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    public static NotificationException avisoNoEncontrado(Long id) {
        return new NotificationException("AVISO_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "No existe el aviso " + id + ".");
    }

    /**
     * Falta la plantilla de un tipo de aviso.
     *
     * <p>Las carga la migracion V1, asi que esto solo ocurre si alguien la
     * borro o desactivo a mano. Se responde 500 y no 404 porque no es un
     * recurso que el cliente pidiera mal: es una pieza del servicio que
     * deberia estar.
     */
    public static NotificationException plantillaAusente(String tipo) {
        return new NotificationException("PLANTILLA_AUSENTE",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "No hay plantilla activa para el aviso de tipo " + tipo
                        + ". Revise la tabla notification_templates.");
    }

    /**
     * Marcar como leido un aviso que no es in-app.
     *
     * <p>Un correo no se puede marcar leido: el servicio no sabe si el
     * destinatario lo abrio, y fingir que lo sabe daria una cifra falsa.
     */
    public static NotificationException soloInApp(String canal) {
        return new NotificationException("SOLO_IN_APP", HttpStatus.CONFLICT,
                "Un aviso por " + canal + " no se puede marcar como leido: el "
                        + "servicio no sabe si el destinatario lo abrio.");
    }

    public static NotificationException transicionInvalida(String desde, String hacia) {
        return new NotificationException("TRANSICION_INVALIDA", HttpStatus.CONFLICT,
                "Un aviso " + desde + " no puede pasar a " + hacia + ".");
    }

    /**
     * Se pidio reenviar un aviso que ya salio.
     *
     * <p>Se rechaza en lugar de duplicarlo: reenviar un correo ya entregado
     * molesta al destinatario y, en un valor critico, siembra la duda de si
     * es un caso nuevo.
     */
    public static NotificationException yaEnviado(Long id) {
        return new NotificationException("YA_ENVIADO", HttpStatus.CONFLICT,
                "El aviso " + id + " ya se envio. Para avisar de nuevo, genere uno nuevo.");
    }

    public static NotificationException sinDestinatario(String tipo) {
        return new NotificationException("SIN_DESTINATARIO", HttpStatus.BAD_REQUEST,
                "No hay a quien enviar el aviso de tipo " + tipo + ".");
    }
}
