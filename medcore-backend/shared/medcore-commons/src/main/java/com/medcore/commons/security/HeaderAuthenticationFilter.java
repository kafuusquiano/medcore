package com.medcore.commons.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Convierte las cabeceras que propaga el gateway en una autenticacion de
 * Spring Security, para que los controllers puedan usar {@code @PreAuthorize}
 * sin volver a validar el JWT (regla MC4).
 *
 * <p>Tambien coloca el {@code requestId} en el MDC, de modo que aparezca en
 * cada linea de log del servicio y una misma operacion se pueda seguir a
 * traves de varios servicios.
 *
 * <p>ADVERTENCIA: este filtro confia sin verificar en {@code X-User-Role}.
 * Eso solo es seguro mientras el microservicio no sea alcanzable desde fuera
 * de la red interna, porque cualquiera que llegue directo puede enviar
 * {@code X-User-Role: ADMIN}. En docker-compose.yml se publica unicamente el
 * puerto del gateway, y el tunel hacia internet expone solo ese puerto.
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    /** Clave del MDC; el patron de log de infra/config-repo la referencia. */
    public static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(GatewayHeaders.REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            MDC.put(MDC_REQUEST_ID, requestId);
        }

        try {
            String userId = request.getHeader(GatewayHeaders.USER_ID);
            String rol = request.getHeader(GatewayHeaders.USER_ROLE);

            boolean hayIdentidad = userId != null && !userId.isBlank()
                    && rol != null && !rol.isBlank();

            // Solo se establece si no hay ya una autenticacion: asi este filtro
            // no pisa la que pueda haber puesto otro mecanismo en el servicio.
            if (hayIdentidad && SecurityContextHolder.getContext().getAuthentication() == null) {
                var autoridad = new SimpleGrantedAuthority(GatewayHeaders.ROLE_PREFIX + rol);
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(autoridad));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Imprescindible: el hilo vuelve al pool y arrastraria el requestId
            // de esta peticion a la siguiente.
            MDC.remove(MDC_REQUEST_ID);
            SecurityContextHolder.clearContext();
        }
    }
}
