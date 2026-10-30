package com.medcore.notification.domain.enums;

import java.util.Set;

/**
 * Estado de un aviso.
 *
 * <pre>
 *   PENDIENTE -> ENVIADA | FALLIDA | OMITIDA
 *   FALLIDA   -> ENVIADA | FALLIDA   (cada reintento)
 *   ENVIADA   -> LEIDA               (solo los avisos in-app)
 * </pre>
 *
 * <p>FALLIDA no es final: el reintento vuelve a intentarlo, y solo tras
 * agotar los intentos se queda ahi. Que el estado lo diga permite saber
 * cuantos avisos no llegaron, que es lo que un sistema de notificaciones no
 * puede ocultar: si el correo de un valor critico no salio, alguien tiene que
 * llamar por telefono.
 */
public enum NotificationStatus {

    /** Creada y a la espera de enviarse. */
    PENDIENTE,

    /** Entregada al canal. En correo significa aceptada por el servidor SMTP. */
    ENVIADA,

    /** El canal rechazo el envio. Admite reintento. */
    FALLIDA,

    /**
     * No se intento enviar.
     *
     * <p>Dos motivos: el canal no esta implementado, o faltaba el dato
     * necesario, por ejemplo un paciente sin correo. Se distingue de FALLIDA
     * a proposito: aqui no hay nada que reintentar, y contarla como fallo
     * ensuciaria las cifras de entrega.
     */
    OMITIDA,

    /** El usuario la abrio. Solo tiene sentido en los avisos in-app. */
    LEIDA;

    public boolean esFinal() {
        return this == ENVIADA || this == OMITIDA || this == LEIDA;
    }

    /** Un aviso fallido se reintenta mientras le queden intentos. */
    public boolean admiteReintento() {
        return this == FALLIDA || this == PENDIENTE;
    }

    public Set<NotificationStatus> siguientesValidos() {
        return switch (this) {
            case PENDIENTE -> Set.of(ENVIADA, FALLIDA, OMITIDA);
            case FALLIDA -> Set.of(ENVIADA, FALLIDA, OMITIDA);
            case ENVIADA -> Set.of(LEIDA);
            case OMITIDA, LEIDA -> Set.of();
        };
    }

    public boolean puedePasarA(NotificationStatus destino) {
        return siguientesValidos().contains(destino);
    }

    public String etiqueta() {
        return switch (this) {
            case PENDIENTE -> "Pendiente";
            case ENVIADA -> "Enviada";
            case FALLIDA -> "Fallida";
            case OMITIDA -> "Omitida";
            case LEIDA -> "Leida";
        };
    }
}
