package com.medcore.notification.domain.enums;

import java.util.List;

/**
 * Que ha ocurrido, y a quien hay que avisar.
 *
 * <p>Cada tipo decide sus canales y su urgencia. Esa decision vive aqui y no
 * repartida por los consumidores para que el criterio sea uno solo: si manana
 * se decide que el recordatorio de cita tambien va por correo, se cambia en un
 * sitio.
 *
 * <p>La distincion entre destinatario paciente y destinatario interno importa:
 * un valor critico se avisa al medico, no al paciente, que no debe recibir por
 * correo un resultado alarmante sin nadie que se lo explique.
 */
public enum NotificationType {

    /** Alta de paciente. Correo de bienvenida con sus datos de acceso. */
    PACIENTE_REGISTRADO(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.EMAIL)),

    /** Cita reservada. El paciente necesita el dia, la hora y el medico. */
    CITA_RESERVADA(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP)),

    /** Cita cancelada. Se avisa siempre, aunque la cancele el propio paciente. */
    CITA_CANCELADA(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP)),

    /** Recordatorio el dia antes de la cita. */
    CITA_RECORDATORIO(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.EMAIL)),

    /**
     * Orden de laboratorio emitida. Lleva las indicaciones de la toma, como el
     * ayuno, que es justamente lo que el paciente necesita saber antes.
     */
    ORDEN_LABORATORIO(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP)),

    /** Muestra recibida en el laboratorio, con el plazo estimado. */
    MUESTRA_REGISTRADA(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.IN_APP)),

    /**
     * Muestra rechazada. Va al MEDICO y no al paciente: quien decide si hay
     * que repetir la toma es el medico, y el paciente recibiria un aviso que
     * no sabe que hacer con el.
     */
    MUESTRA_RECHAZADA(Destinatario.INTERNO, true,
            List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP)),

    /** Resultado listo. Se avisa de que esta disponible, sin el valor. */
    RESULTADO_LISTO(Destinatario.PACIENTE, false,
            List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP)),

    /**
     * Valor critico. Urgente y al MEDICO.
     *
     * <p>Nunca al paciente: un resultado alarmante por correo, sin nadie que
     * lo interprete ni indique que hacer, es peor que no avisar. El medico es
     * quien tiene que localizarlo.
     */
    VALOR_CRITICO(Destinatario.INTERNO, true,
            List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP)),

    /**
     * Reactivo por debajo del minimo. Al buzon del almacen.
     *
     * <p>Solo por correo, y no in-app: los eventos de inventario no traen
     * ningun usuario ni paciente, porque el almacen no tiene responsable
     * asignado en el modelo y el aviso es para quien este de turno. Un
     * aviso in-app sin destinatario no aparece en ninguna bandeja: se
     * creaba y quedaba siempre OMITIDO, lo que se vio contra la base real.
     *
     * <p>Para que tambien fuera in-app haria falta poder dirigir un aviso
     * a un ROL y no a una persona, que es una mejora pendiente.
     */
    STOCK_BAJO(Destinatario.INTERNO, false,
            List.of(NotificationChannel.EMAIL)),

    /**
     * Lote vencido o por vencer. Urgente si ya vencio: hay material no
     * apto en la estanteria y alguien puede usarlo.
     *
     * <p>Solo por correo, por el mismo motivo que STOCK_BAJO: sin usuario
     * al que dirigirlo, el aviso in-app quedaba siempre omitido.
     */
    LOTE_VENCIDO(Destinatario.INTERNO, true,
            List.of(NotificationChannel.EMAIL));

    /** A quien va dirigido el aviso. */
    public enum Destinatario {
        /** Al paciente, a su correo. */
        PACIENTE,
        /** Al personal de la clinica: medico, laboratorio o almacen. */
        INTERNO
    }

    private final Destinatario destinatario;
    private final boolean urgente;
    private final List<NotificationChannel> canales;

    NotificationType(Destinatario destinatario, boolean urgente,
                     List<NotificationChannel> canales) {
        this.destinatario = destinatario;
        this.urgente = urgente;
        this.canales = canales;
    }

    public Destinatario destinatario() {
        return destinatario;
    }

    /**
     * Un aviso urgente se envia de inmediato y nunca se agrupa, ademas de
     * registrarse con nivel WARN para que destaque en el log.
     */
    public boolean esUrgente() {
        return urgente;
    }

    public List<NotificationChannel> canales() {
        return List.copyOf(canales);
    }

    public boolean esParaPaciente() {
        return destinatario == Destinatario.PACIENTE;
    }

    public String etiqueta() {
        return switch (this) {
            case PACIENTE_REGISTRADO -> "Alta de paciente";
            case CITA_RESERVADA -> "Cita reservada";
            case CITA_CANCELADA -> "Cita cancelada";
            case CITA_RECORDATORIO -> "Recordatorio de cita";
            case ORDEN_LABORATORIO -> "Orden de laboratorio";
            case MUESTRA_REGISTRADA -> "Muestra recibida";
            case MUESTRA_RECHAZADA -> "Muestra rechazada";
            case RESULTADO_LISTO -> "Resultado disponible";
            case VALOR_CRITICO -> "Valor critico";
            case STOCK_BAJO -> "Stock bajo";
            case LOTE_VENCIDO -> "Lote vencido";
        };
    }
}
