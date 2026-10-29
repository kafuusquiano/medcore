package com.medcore.patient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

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
}
