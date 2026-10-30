package com.medcore.notification.domain.dto.response;

import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;

import java.time.Instant;

/**
 * Vista de un aviso.
 *
 * @param urgente       calculado del tipo, para que el frontend destaque el
 *                      aviso sin repetir la lista de tipos urgentes
 * @param leido         true solo en los in-app que el usuario abrio
 * @param motivoFallo   por que no llego. Se devuelve a proposito: un aviso
 *                      fallido sin motivo no sirve para averiguar nada, y en
 *                      el panel de avisos perdidos es el dato principal.
 * @param intentosRestantes cuantos quedan antes de darlo por perdido
 */
public record NotificationResponse(
        Long id,
        NotificationType tipo,
        String etiquetaTipo,
        NotificationChannel canal,
        String etiquetaCanal,
        NotificationStatus estado,
        String etiquetaEstado,
        boolean urgente,
        boolean leido,
        String destinatario,
        Long idPaciente,
        Long idUsuario,
        String asunto,
        String mensaje,
        String enlace,
        int intentos,
        int intentosRestantes,
        String motivoFallo,
        Instant fechaProgramada,
        Instant fechaEnvio,
        Instant fechaLectura,
        Instant fechaCreacion
) {
}
