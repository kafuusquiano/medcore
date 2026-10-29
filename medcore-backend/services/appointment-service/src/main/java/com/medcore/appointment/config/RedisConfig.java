package com.medcore.appointment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis para la cache de disponibilidad de slots.
 *
 * <p>Es una cache, no la fuente de verdad: si Redis cae, la disponibilidad se
 * sigue leyendo de la base y las reservas no se detienen.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
