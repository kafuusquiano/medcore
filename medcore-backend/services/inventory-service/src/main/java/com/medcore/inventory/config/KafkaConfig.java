package com.medcore.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * Habilita Kafka.
 *
 * <p>inventory-service solo produce: publica stock.low y stock.expired. No
 * consume ningun topico.
 *
 * <p>No define beans de productor: los serializadores, la idempotencia y los
 * reintentos viven en infra/config-repo/application.yml, conforme a MC3.
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    /**
     * Convierte el JSON del mensaje al tipo que declara cada
     * {@code @KafkaListener}.
     *
     * <p>Se registra aunque este servicio no consuma todavia: es el bean que
     * el resto de servicios necesita para no depender de la cabecera
     * __TypeId__, y tenerlo desde el principio evita que el primer consumidor
     * que se anada falle con "No type information in headers" (regla MC8).
     */
    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }
}
