package com.medcore.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis para la lista negra de tokens revocados en el logout.
 *
 * <p>Se usa {@link StringRedisTemplate} y no RedisTemplate generico porque las
 * claves y los valores son texto plano: el serializador JDK por defecto
 * escribiria claves binarias ilegibles al inspeccionarlas con redis-cli.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
