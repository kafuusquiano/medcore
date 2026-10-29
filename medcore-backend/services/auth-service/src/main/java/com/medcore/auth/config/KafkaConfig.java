package com.medcore.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * Habilita los listeners de Kafka.
 *
 * <p>No define beans de producer ni de consumer: la configuracion completa
 * (serializadores, idempotencia, ack-mode, paquetes confiables) vive en
 * infra/config-repo/application.yml, conforme a MC3.
 *
 * <p>auth-service solo consume: escucha patient.created para crear la cuenta
 * del paciente. No publica ningun evento.
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
     * incluido un reenvio manual, y no queda acoplado al nombre completo de
     * la clase Java del que lo publico.
     */
    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }
}
