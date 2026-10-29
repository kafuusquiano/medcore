package com.medcore.auth.config;

import com.medcore.commons.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad de auth-service.
 *
 * <p>Este servicio no valida JWT: de eso se encarga el gateway. Aqui solo se
 * leen las cabeceras que aquel propaga (MC4) mediante
 * {@link HeaderAuthenticationFilter}, para poder usar {@code @PreAuthorize}
 * en los controllers.
 *
 * <p>Sin estado y sin CSRF porque no hay sesion de servidor ni formularios:
 * la autenticacion viaja en cada peticion.
 *
 * <p>CORS no se configura aqui. Lo resuelve el gateway, que es el unico
 * expuesto al navegador; duplicarlo generaria cabeceras repetidas.
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
                        // Login y refresh son publicos por definicion: todavia
                        // no hay identidad que propagar.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // El resto exige identidad; el rol se comprueba con
                        // @PreAuthorize en cada metodo.
                        .anyRequest().authenticated())
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * BCrypt con coste configurable. Cada unidad duplica el tiempo de calculo;
     * 10 es el equilibrio habitual entre resistencia a fuerza bruta y latencia
     * aceptable en el login.
     */
    @Bean
    public PasswordEncoder passwordEncoder(AuthProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}
