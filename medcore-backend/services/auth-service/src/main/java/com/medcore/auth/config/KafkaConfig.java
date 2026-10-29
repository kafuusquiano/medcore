package com.medcore.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

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
}
