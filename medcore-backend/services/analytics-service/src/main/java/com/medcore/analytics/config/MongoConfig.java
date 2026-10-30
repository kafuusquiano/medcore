package com.medcore.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Repositorios de MongoDB.
 *
 * <p>Se declara el paquete explicitamente aunque este servicio no use JPA: asi
 * el escaneo no depende de donde este la clase de arranque, y queda escrito
 * donde viven los repositorios.
 *
 * <p>No se activa la creacion de indices desde la aplicacion: los declara
 * {@code infra/mongo/init/01-init-collections.js}, que es a MongoDB lo que las
 * migraciones de Flyway son a Postgres. Con
 * {@code auto-index-creation} en true, Spring Data intentaria crear desde
 * {@code @Indexed} indices que ya existen y fallaria con
 * IndexOptionsConflict en cuanto los nombres no coincidieran, que es lo que
 * ocurrio en laboratory-service (regla MC8).
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.medcore.analytics.repository")
public class MongoConfig {
}
