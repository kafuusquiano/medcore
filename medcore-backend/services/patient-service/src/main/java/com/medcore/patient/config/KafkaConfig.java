package com.medcore.patient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * Habilita Kafka.
 *
 * <p>patient-service solo produce: publica {@code patient.created} y
 * {@code patient.updated}. No consume ningun topico.
 *
 * <p>No define beans de productor: los serializadores, la idempotencia y los
 * reintentos viven en {@code infra/config-repo/application.yml}, conforme a
 * MC3, para poder ajustarlos sin reconstruir la imagen.
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
