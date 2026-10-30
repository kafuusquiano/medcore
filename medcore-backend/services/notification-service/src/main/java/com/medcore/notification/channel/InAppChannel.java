package com.medcore.notification.channel;

import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Aviso dentro de la aplicacion.
 *
 * <p>No hace nada: el aviso ya esta guardado en la base cuando llega aqui, y
 * eso ES la entrega. El frontend consulta su bandeja y marca los avisos como
 * leidos.
 *
 * <p>Se resolvio asi en lugar de empujar por WebSocket a proposito. Un aviso
 * que solo viaja por un socket abierto se pierde si el usuario no esta
 * conectado, que es justamente cuando hace falta que le llegue; guardarlo en
 * la base lo deja esperando hasta que entre. Anadir WebSocket despues es
 * notificar sobre lo que ya esta guardado, no sustituirlo.
 *
 * <p>Existe como clase, pese a no enviar nada, para que el dispatcher trate
 * todos los canales igual: sin ella haria falta un caso especial, y entonces
 * el estado del aviso in-app dependeria de una rama distinta del codigo.
 */
@Component
public class InAppChannel implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(InAppChannel.class);

    @Override
    public NotificationChannel canal() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public Resultado enviar(Notification aviso, Map<String, String> datos) {
        // Sin a quien mostrarselo, el aviso no aparecera en ninguna bandeja.
        if (aviso.getIdPaciente() == null && aviso.getIdUsuario() == null) {
            return Resultado.falloDefinitivo(
                    "El aviso in-app no indica a que paciente ni a que usuario corresponde.");
        }

        log.debug("Aviso in-app de {} disponible para paciente={} usuario={}",
                aviso.getTipo(), aviso.getIdPaciente(), aviso.getIdUsuario());

        return Resultado.ok();
    }
}
