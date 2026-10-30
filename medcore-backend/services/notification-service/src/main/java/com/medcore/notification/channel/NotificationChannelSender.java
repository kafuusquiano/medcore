package com.medcore.notification.channel;

import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationChannel;

import java.util.Map;

/**
 * Un canal de salida.
 *
 * <p>Cada implementacion sabe enviar por un medio. El dispatcher las localiza
 * por {@link #canal()} y no con una cadena de condiciones, de modo que anadir
 * un canal sea escribir una clase.
 *
 * <p>Un sender NO lanza excepcion cuando el envio falla: devuelve un
 * {@link Resultado} que dice si salio y por que no. El fallo de un correo es
 * un desenlace previsto, no un error del programa, y el dispatcher necesita
 * distinguir entre "reintentalo" y "no hay nada que reintentar".
 */
public interface NotificationChannelSender {

    NotificationChannel canal();

    /**
     * Envia el aviso.
     *
     * @param datos valores del evento, para rellenar la plantilla HTML del
     *              correo. El asunto y el texto ya vienen resueltos en el
     *              propio aviso.
     */
    Resultado enviar(Notification aviso, Map<String, String> datos);

    /**
     * Desenlace de un envio.
     *
     * @param motivo explicacion cuando no se envio. Se guarda en el aviso,
     *               porque uno que no llego sin motivo no sirve para
     *               averiguar nada.
     */
    record Resultado(boolean enviado, boolean reintentable, String motivo) {

        public static Resultado ok() {
            return new Resultado(true, false, null);
        }

        /**
         * Fallo que puede resolverse al reintentar: el servidor de correo
         * caido, un corte de red.
         */
        public static Resultado falloTemporal(String motivo) {
            return new Resultado(false, true, motivo);
        }

        /**
         * Fallo que no se arregla reintentando: una direccion invalida, un
         * canal sin implementar, un dato que falta.
         */
        public static Resultado falloDefinitivo(String motivo) {
            return new Resultado(false, false, motivo);
        }
    }
}
