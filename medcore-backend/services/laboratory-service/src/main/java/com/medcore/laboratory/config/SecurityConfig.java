package com.medcore.laboratory.config;

import com.medcore.commons.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad de laboratory-service.
 *
 * <p>No valida JWT (MC4): lee las cabeceras del gateway con
 * HeaderAuthenticationFilter. CORS lo resuelve el gateway.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // Los analizadores de laboratorio no saben enviar un
                        // JWT: abren una conexion y escriben la trama. Esta
                        // ruta se protege por red y no por token: en
                        // produccion solo la VLAN del laboratorio alcanza el
                        // puerto del servicio, y el gateway no publica
                        // /api/hl7/** hacia el exterior.
                        //
                        // El permitAll cubre solo la recepcion. La
                        // exportacion, que es GET y saca datos clinicos del
                        // sistema, exige rol igual que el resto.
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/hl7/oru").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
