package com.medcore.notification.channel;

import com.medcore.notification.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Canales declarados pero sin implementacion: PUSH y SMS.
 *
 * <p>Firebase necesita un proyecto con credenciales y el SMS una pasarela con
 * contrato, y ninguna de las dos cosas existe en el proyecto. En lugar de
 * escribir una integracion que no se puede probar, o de simular un envio que
 * nunca ocurre, el aviso se marca OMITIDA con el motivo.
 *
 * <p>Esa diferencia importa: un aviso OMITIDO no cuenta como fallo y no se
 * reintenta, pero queda visible en el panel con la razon. Si se simulara un
 * envio correcto, el sistema diria que aviso cuando no lo hizo, que en un
 * valor critico es peor que no tener el canal.
 *
 * <p>Para activar uno de los dos: escribir su sender con el canal que
 * corresponda y quitarlo de esta clase. El dispatcher lo localiza por
 * {@code canal()}, asi que no hay nada mas que cambiar.
 */
@Component
public class UnsupportedChannel {

    private static final Logger log = LoggerFactory.getLogger(UnsupportedChannel.class);

    /** Los canales que este servicio declara pero no sabe enviar. */
    private static final List<NotificationChannel> SIN_IMPLEMENTAR =
            List.of(NotificationChannel.PUSH, NotificationChannel.SMS);

    /**
     * Senders de relleno, uno por canal sin implementar.
     *
     * <p>Se registran como beans para que el dispatcher los encuentre por el
     * mismo camino que los reales: asi no necesita saber que hay canales
     * especiales.
     */
    public List<NotificationChannelSender> senders() {
        return SIN_IMPLEMENTAR.stream()
                .map(canal -> (NotificationChannelSender) new NotificationChannelSender() {
                    @Override
                    public NotificationChannel canal() {
                        return canal;
                    }

                    @Override
                    public Resultado enviar(com.medcore.notification.domain.entity.Notification aviso,
                                            Map<String, String> datos) {
                        log.debug("Aviso de {} omitido: {}",
                                aviso.getTipo(), canal.motivoNoDisponible());
                        // Definitivo y no temporal: reintentarlo no va a
                        // configurar el canal.
                        return Resultado.falloDefinitivo(canal.motivoNoDisponible());
                    }
                })
                .toList();
    }
}
