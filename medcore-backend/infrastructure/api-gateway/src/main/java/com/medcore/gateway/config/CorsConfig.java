package com.medcore.gateway.config;

/**
 * CORS del gateway.
 *
 * <p>No hay beans aqui a proposito: la configuracion vive en
 * {@code spring.cloud.gateway.server.webflux.globalcors} dentro de
 * {@code infra/config-repo/api-gateway.yml}, que sirve el config-server.
 *
 * <p>Se eligio asi porque el origen permitido cambia entre entornos: en local
 * es {@code http://localhost:3000} y en el despliegue es la URL de Vercel.
 * Teniendolo en configuracion, cambiar de dominio no obliga a reconstruir la
 * imagen; basta con {@code FRONTEND_ORIGIN}.
 *
 * <p>Dos detalles que deben respetarse en ese YAML:
 * <ul>
 *   <li>{@code allowed-headers: "*"}, para que pase
 *       {@code ngrok-skip-browser-warning}. Sin esa cabecera el navegador
 *       recibe la pagina intersticial de ngrok en lugar del JSON.</li>
 *   <li>{@code exposed-headers: [X-Request-Id]}, para que el frontend pueda
 *       leer el identificador de la peticion y reportarlo ante un error.</li>
 * </ul>
 *
 * <p>Definir aqui un {@code CorsWebFilter} ademas de la configuracion del
 * gateway duplicaria las cabeceras {@code Access-Control-Allow-Origin} y el
 * navegador rechazaria la respuesta.
 */
public final class CorsConfig {

    private CorsConfig() {
        throw new AssertionError("Clase de documentacion, no instanciable");
    }
}
