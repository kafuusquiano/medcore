package com.medcore.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * Habilita Kafka.
 *
 * <p>notification-service es el consumidor con mas topicos del sistema: nueve
 * en total, y no publica ninguno. Es el extremo de todas las cadenas de
 * eventos.
 *
 * <pre>
 *   patient.created        -> bienvenida
 *   appointment.created    -> confirmacion y recordatorio programado
 *   appointment.cancelled  -> aviso de cancelacion
 *   lab-order.created      -> indicaciones de la toma
 *   sample.registered      -> muestra recibida, o rechazada al medico
 *   result.registered      -> resultado disponible, sin el valor
 *   result.critical        -> valor critico al medico, con el valor
 *   stock.low              -> reponer reactivo
 *   stock.expired          -> retirar lote
 * </pre>
 *
 * <p>La configuracion de deserializadores y el ack-mode viven en
 * infra/config-repo/application.yml, conforme a MC3.
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    /**
     * Convierte el JSON del mensaje al tipo que declara cada
     * {@code @KafkaListener}.
     *
     * <p>Se usa en lugar de JsonDeserializer para no depender de la cabecera
     * __TypeId__: asi el consumidor acepta eventos de cualquier productor,
     * incluido un reenvio manual, y no queda acoplado al nombre completo de la
     * clase Java del que lo publico (regla MC8).
     *
     * <p>En este servicio importa mas que en ninguno: consume eventos de cinco
     * productores distintos, y bastaria que uno cambiara el paquete de su
     * clase de evento para que el consumidor dejara de funcionar.
     */
    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }
}
