package com.medcore.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del servicio, de
 * {@code infra/config-repo/notification-service.yml}.
 *
 * <p>Se leen con {@code @ConfigurationProperties} y no con {@code @Value}
 * porque el config-server entrega las estructuras anidadas como claves
 * indexadas y {@code @Value} falla al resolverlas.
 *
 * @param maxReintentos intentos de envio antes de darlo por perdido. Tres es
 *                      el valor de partida: cubre un corte breve del servidor
 *                      de correo sin insistir indefinidamente sobre una
 *                      direccion que no existe.
 * @param criticosInmediatos los valores criticos se envian al momento y nunca
 *                      se agrupan. Esta como parametro para poder apagarlo en
 *                      una demostracion, no porque convenga apagarlo.
 * @param correoInterno buzon de la clinica para los avisos del personal.
 *                      Hace falta porque NINGUN evento trae el correo del
 *                      medico ni del laboratorista: los eventos llevan su
 *                      identificador, no su direccion, y pedirsela a
 *                      auth-service en cada aviso acoplaria este servicio a
 *                      otro para un dato que no cambia. El aviso in-app si
 *                      llega a la persona concreta, por su identificador.
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        String remitente,
        String remitenteNombre,
        String correoInterno,
        Integer maxReintentos,
        Boolean criticosInmediatos,
        String cronEnvio,
        String cronRecordatorios
) {

    private static final String REMITENTE_POR_OMISION = "no-responder@medcore.local";
    private static final String NOMBRE_POR_OMISION = "MedCore";
    private static final String INTERNO_POR_OMISION = "laboratorio@medcore.local";
    private static final int REINTENTOS_POR_OMISION = 3;

    /**
     * Remitente efectivo, con valor por omision.
     *
     * <p>Sin este respaldo, un arranque con el config-server a medias dejaria
     * el remitente en null y cada envio fallaria con NullPointerException en
     * lugar de salir.
     */
    @Override
    public String remitente() {
        return remitente == null || remitente.isBlank()
                ? REMITENTE_POR_OMISION
                : remitente;
    }

    @Override
    public String remitenteNombre() {
        return remitenteNombre == null || remitenteNombre.isBlank()
                ? NOMBRE_POR_OMISION
                : remitenteNombre;
    }

    /**
     * Un tope de 0 dejaria todos los avisos sin enviar, y uno negativo no
     * significa nada: los dos caen al valor por omision.
     */
    @Override
    public Integer maxReintentos() {
        return maxReintentos == null || maxReintentos < 1
                ? REINTENTOS_POR_OMISION
                : maxReintentos;
    }

    /**
     * Buzon interno efectivo.
     *
     * <p>Sin el, los avisos del personal se crearian sin direccion y saldrian
     * todos omitidos: es mejor que lleguen a un buzon general que no lleguen.
     */
    @Override
    public String correoInterno() {
        return correoInterno == null || correoInterno.isBlank()
                ? INTERNO_POR_OMISION
                : correoInterno;
    }

    public boolean enviarCriticosDeInmediato() {
        return criticosInmediatos == null || criticosInmediatos;
    }
}
