package com.medcore.patient.config;

import com.medcore.commons.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad de patient-service.
 *
 * <p>No valida JWT (MC4): lee las cabeceras que propaga el gateway mediante
 * {@link HeaderAuthenticationFilter}, de modo que {@code @PreAuthorize}
 * funcione sin que este servicio conozca la clave de firma.
 *
 * <p>Sin estado y sin CSRF: no hay sesion de servidor ni formularios, la
 * identidad viaja en cada peticion.
 *
 * <p>CORS lo resuelve el gateway, unico componente expuesto al navegador.
 * Configurarlo tambien aqui duplicaria la cabecera Access-Control-Allow-Origin
 * y el navegador rechazaria la respuesta.
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
                        // El resto exige identidad; el rol concreto se
                        // comprueba con @PreAuthorize en cada metodo.
                        .anyRequest().authenticated())
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
