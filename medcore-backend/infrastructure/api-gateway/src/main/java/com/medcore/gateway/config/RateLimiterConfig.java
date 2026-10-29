package com.medcore.gateway.config;

import com.medcore.commons.security.GatewayHeaders;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Limitacion de tasa con algoritmo de cubeta de tokens, respaldada en Redis.
 *
 * <p>El estado vive en Redis y no en memoria del gateway, de modo que el limite
 * se respeta aunque haya mas de una instancia del gateway.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * La cuota se cuenta por usuario autenticado. Para las rutas publicas,
     * donde todavia no hay identidad, se usa la IP de origen: de lo contrario
     * todos los intentos de login del mundo compartirian una sola cuota.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            var remoto = exchange.getRequest().getRemoteAddress();
            String ip = (remoto == null) ? "desconocida" : remoto.getAddress().getHostAddress();
            return Mono.just("ip:" + ip);
        };
    }

    @Bean
    public RedisRateLimiter redisRateLimiter(GatewayProperties properties) {
        GatewayProperties.RateLimit limite = properties.rateLimit();
        return new RedisRateLimiter(
                limite.replenishRate(),
                limite.burstCapacity(),
                limite.requestedTokens());
    }
}
