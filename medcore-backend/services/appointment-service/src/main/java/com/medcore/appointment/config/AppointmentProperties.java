package com.medcore.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametros de la agenda, bajo el prefijo {@code appointment}.
 * Los valores llegan de {@code infra/config-repo/appointment-service.yml}.
 *
 * @param slotDuracionMinutos        duracion de cada franja
 * @param horizonteDias              dias hacia adelante para los que se generan slots
 * @param cancelacionAntelacionHoras antelacion minima para cancelar sin penalidad
 * @param cacheSlotsTtlSegundos      vigencia de la disponibilidad cacheada en Redis
 */
@ConfigurationProperties(prefix = "appointment")
public record AppointmentProperties(
        @DefaultValue("30") int slotDuracionMinutos,
        @DefaultValue("30") int horizonteDias,
        @DefaultValue("24") int cancelacionAntelacionHoras,
        @DefaultValue("120") long cacheSlotsTtlSegundos
) {
}
