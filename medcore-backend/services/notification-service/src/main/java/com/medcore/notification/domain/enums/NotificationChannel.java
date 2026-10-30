package com.medcore.notification.domain.enums;

/**
 * Por donde sale un aviso.
 *
 * <p>Solo EMAIL e IN_APP estan implementados. PUSH y SMS se declaran porque el
 * modelo los contempla, pero no hay forma de enviarlos: Firebase necesita un
 * proyecto con credenciales y el SMS una pasarela con contrato, y ninguna de
 * las dos cosas existe en el proyecto.
 *
 * <p>Se declaran en lugar de omitirse para que anadirlos despues sea escribir
 * una clase y no migrar los datos ya guardados. Un envio por un canal sin
 * implementacion no se simula: el dispatcher lo marca como OMITIDO con el
 * motivo, de modo que en el panel se vea por que no salio el aviso.
 */
public enum NotificationChannel {

    /** Correo electronico. En desarrollo va a Mailpit. */
    EMAIL(true),

    /** Aviso dentro de la aplicacion, que el usuario lee y marca como leido. */
    IN_APP(true),

    /** Push a movil. Requiere un proyecto de Firebase con credenciales. */
    PUSH(false),

    /** Mensaje de texto. Requiere una pasarela con contrato. */
    SMS(false);

    private final boolean implementado;

    NotificationChannel(boolean implementado) {
        this.implementado = implementado;
    }

    public boolean estaImplementado() {
        return implementado;
    }

    /** Explicacion que se guarda cuando el envio se omite. */
    public String motivoNoDisponible() {
        return switch (this) {
            case PUSH -> "Canal PUSH no configurado: falta un proyecto de Firebase "
                    + "con sus credenciales.";
            case SMS -> "Canal SMS no configurado: falta una pasarela de mensajeria.";
            case EMAIL, IN_APP -> null;
        };
    }

    public String etiqueta() {
        return switch (this) {
            case EMAIL -> "Correo";
            case IN_APP -> "En la aplicacion";
            case PUSH -> "Push";
            case SMS -> "SMS";
        };
    }
}
