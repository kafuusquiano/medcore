package com.medcore.laboratory.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Reparto de repositorios entre las dos bases del servicio.
 *
 * <p>laboratory-service es el unico microservicio que usa Postgres y MongoDB
 * a la vez (MC1): en lab_db van las muestras y los resultados interpretados,
 * de forma fija y consulta relacional, y en MongoDB la trama cruda del
 * equipo, cuya estructura cambia con cada fabricante.
 *
 * <p>Esa convivencia obliga a declarar explicitamente que paquete pertenece a
 * cada tecnologia. Sin acotarlo, Spring Data recorre el mismo paquete con los
 * dos escaneos e intenta tratar los repositorios de Postgres como documentos
 * de Mongo, o al contrario, segun el orden de arranque. De ahi que los
 * repositorios de Mongo vivan en {@code repository.mongo} y los de JPA
 * directamente en {@code repository}.
 *
 * <p>La creacion de indices de Mongo se activa en la configuracion del
 * servicio. En el indice unico sobre {@code idMuestra} de ResultDocument se
 * confia para que un reenvio del equipo no deje dos documentos de la misma
 * muestra.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.medcore.laboratory.repository",
        // El escaneo de un paquete incluye sus subpaquetes, asi que hay que
        // excluir repository.mongo a mano. Spring Data sabria descartarlo por
        // el tipo base de la interfaz, pero dejarlo escrito evita depender de
        // esa deteccion y dice de un vistazo donde esta la frontera.
        excludeFilters = @ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.REGEX,
                pattern = "com[.]medcore[.]laboratory[.]repository[.]mongo[.].*"))
@EnableMongoRepositories(basePackages = "com.medcore.laboratory.repository.mongo")
public class PersistenceConfig {
}
