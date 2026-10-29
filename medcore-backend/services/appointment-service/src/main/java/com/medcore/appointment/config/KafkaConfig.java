package com.medcore.appointment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Habilita Kafka.
 *
 * <p>Produce appointment.created y appointment.cancelled; consume
 * patient.created. La configuracion de serializadores e idempotencia vive en
 * infra/config-repo/application.yml (MC3).
 */
@EnableKafka
@Configuration
public class KafkaConfig {
}
