package com.medcore.gateway.security;

import com.medcore.gateway.config.GatewayProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Decide que peticiones pueden pasar sin JWT.
 *
 * <p>La lista viaja en {@code gateway.rutas-publicas}, que sirve el
 * config-server, de modo que abrir o cerrar un endpoint no obliga a
 * reconstruir la imagen del gateway.
 */
@Component
public class RouteValidator {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> rutasPublicas;

    public RouteValidator(GatewayProperties properties) {
        this.rutasPublicas = List.copyOf(properties.rutasPublicas());
    }

    /**
     * Indica si la peticion exige un token valido.
     *
     * <p>Las preflight CORS nunca llevan Authorization, asi que se dejan pasar:
     * de lo contrario el navegador recibiria 401 antes de enviar la peticion real.
     */
    public boolean requiereAutenticacion(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return false;
        }
        String ruta = request.getURI().getPath();
        return rutasPublicas.stream().noneMatch(patron -> matcher.match(patron, ruta));
    }
}
