package com.medcore.notification.mapper;

import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.dto.response.NotificationResponse;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationStatus;
import org.springframework.stereotype.Component;

/** Conversion de un aviso a su vista. */
@Component
public class NotificationMapper {

    private final NotificationProperties properties;

    public NotificationMapper(NotificationProperties properties) {
        this.properties = properties;
    }

    public NotificationResponse aRespuesta(Notification aviso) {
        return new NotificationResponse(
                aviso.getId(),
                aviso.getTipo(),
                aviso.getTipo().etiqueta(),
                aviso.getCanal(),
                aviso.getCanal().etiqueta(),
                aviso.getEstado(),
                aviso.getEstado().etiqueta(),
                // Calculados, para que el frontend no repita las reglas.
                aviso.getTipo().esUrgente(),
                aviso.getEstado() == NotificationStatus.LEIDA,
                aviso.getDestinatario(),
                aviso.getIdPaciente(),
                aviso.getIdUsuario(),
                aviso.getAsunto(),
                aviso.getMensaje(),
                aviso.getEnlace(),
                aviso.getReferencia(),
                aviso.getIntentos(),
                Math.max(0, properties.maxReintentos() - aviso.getIntentos()),
                aviso.getMotivoFallo(),
                aviso.getFechaProgramada(),
                aviso.getFechaEnvio(),
                aviso.getFechaLectura(),
                aviso.getFechaCreacion());
    }
}
