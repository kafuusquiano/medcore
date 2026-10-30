package com.medcore.analytics.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Centroides de las zonas, para situarlas en el mapa de calor.
 *
 * <p>Se cargan una vez al arrancar desde
 * {@code resources/geo/centroides-lima-callao.geojson} y quedan en memoria:
 * son 43 puntos que no cambian, y leer el archivo en cada peticion del mapa
 * seria trabajo repetido para nada.
 *
 * <p>Si el archivo falta o esta mal formado, el servicio arranca igual y el
 * mapa devuelve las zonas sin coordenadas. Dejar sin arrancar todo el
 * servicio de analitica por un archivo de adorno geografico seria
 * desproporcionado: los recuentos y las alertas no dependen de el.
 */
@Component
public class GeoCentroidCatalog {

    private static final Logger log = LoggerFactory.getLogger(GeoCentroidCatalog.class);

    private static final String RUTA = "geo/centroides-lima-callao.geojson";

    private final ObjectMapper objectMapper;
    private final Map<String, Centroide> porZona = new HashMap<>();

    public GeoCentroidCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @param latitud y {@code longitud} en grados decimales, CRS84. */
    public record Centroide(double latitud, double longitud) {
    }

    @PostConstruct
    void cargar() {
        ClassPathResource recurso = new ClassPathResource(RUTA);

        if (!recurso.exists()) {
            log.warn("No se encontro {}; el mapa de calor saldra sin coordenadas", RUTA);
            return;
        }

        try (InputStream entrada = recurso.getInputStream()) {
            JsonNode raiz = objectMapper.readTree(entrada);

            for (JsonNode feature : raiz.path("features")) {
                String zona = feature.path("properties").path("zona").asText(null);
                JsonNode coordenadas = feature.path("geometry").path("coordinates");

                if (zona == null || !coordenadas.isArray() || coordenadas.size() < 2) {
                    continue;
                }

                // GeoJSON guarda longitud primero y latitud despues. Invertirlo
                // situa Lima en medio de China, que es el error clasico al leer
                // este formato.
                double longitud = coordenadas.get(0).asDouble();
                double latitud = coordenadas.get(1).asDouble();

                porZona.put(normalizar(zona), new Centroide(latitud, longitud));
            }

            log.info("Cargados {} centroides de zona desde {}", porZona.size(), RUTA);

        } catch (IOException e) {
            log.error("No se pudo leer {}: {}. El mapa de calor saldra sin coordenadas.",
                    RUTA, e.getMessage());
        }
    }

    /**
     * Centroide de una zona, si figura en el archivo.
     *
     * <p>Devuelve vacio para las zonas que no estan, incluida la marca
     * "SIN ZONA": el panel las muestra en la tabla pero no en el mapa, que es
     * mejor que situarlas en un punto inventado.
     */
    public Optional<Centroide> de(String zona) {
        if (zona == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porZona.get(normalizar(zona)));
    }

    public int zonasConocidas() {
        return porZona.size();
    }

    /**
     * Normaliza el nombre para comparar.
     *
     * <p>Las zonas llegan tecleadas en el padron de pacientes, asi que "San
     * Juan de Lurigancho", "SAN JUAN DE LURIGANCHO" y un nombre con tildes
     * tienen que resolver al mismo centroide. Se quitan los diacriticos y se
     * pasa a mayusculas.
     */
    private String normalizar(String zona) {
        String sinTildes = java.text.Normalizer
                .normalize(zona.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toUpperCase().replaceAll("\\s+", " ");
    }
}
