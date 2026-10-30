package com.medcore.analytics.repository;

import com.medcore.analytics.domain.document.AnalyticsRecord;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.analytics.repository.projection.ConteoPorZonaYPrueba;
import com.medcore.analytics.repository.projection.ZonaProjection;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Registros epidemiologicos y sus agregaciones.
 *
 * <p>Las agregaciones se escriben a mano con {@code @Aggregation} y no se
 * resuelven cargando documentos en memoria: contar casos por zona sobre un
 * ano de resultados son cientos de miles de documentos, y el trabajo lo tiene
 * que hacer Mongo.
 */
public interface AnalyticsRecordRepository extends MongoRepository<AnalyticsRecord, String> {

    /**
     * Comprueba si el resultado ya se registro.
     *
     * <p>Hace idempotente el consumo: Kafka puede entregar el mismo evento dos
     * veces, y un caso contado doble falsea la vigilancia.
     */
    Optional<AnalyticsRecord> findByIdResultado(Long idResultado);

    boolean existsByIdResultado(Long idResultado);

    /*
     * NINGUNA consulta de este repositorio usa el "Between" de los metodos
     * derivados. En Spring Data MongoDB, Between se traduce a $gt y $lt, es
     * decir EXCLUSIVO en los dos extremos, al contrario que en JPA, donde
     * BETWEEN incluye los limites.
     *
     * Con rangos de fechas eso descarta el primer y el ultimo dia de cada
     * ventana. Se detecto contra la base real: la deteccion puntual de brotes
     * contaba 0 casos donde la agregacion contaba 6, porque todos eran de
     * hoy, que es justo el limite superior.
     *
     * Por eso los rangos se escriben a mano con $gte y $lte.
     */

    @Query(value = "{ zona: ?0, fecha: { $gte: ?1, $lte: ?2 } }",
            sort = "{ fecha: -1 }")
    List<AnalyticsRecord> casosDeZonaEntre(String zona, LocalDate desde, LocalDate hasta);

    @Query(value = "{ critico: true, fecha: { $gte: ?0, $lte: ?1 } }",
            sort = "{ fechaResultado: -1 }")
    List<AnalyticsRecord> criticosEntre(LocalDate desde, LocalDate hasta);

    @Query(value = "{ fecha: { $gte: ?0, $lte: ?1 } }", count = true)
    long contarEntre(LocalDate desde, LocalDate hasta);

    @Query(value = "{ anormal: true, fecha: { $gte: ?0, $lte: ?1 } }", count = true)
    long contarAnormalesEntre(LocalDate desde, LocalDate hasta);

    @Query(value = "{ critico: true, fecha: { $gte: ?0, $lte: ?1 } }", count = true)
    long contarCriticosEntre(LocalDate desde, LocalDate hasta);

    /**
     * Casos anormales de una prueba concreta en una zona, dentro de la
     * ventana. Es la consulta que dispara la deteccion de brotes.
     *
     * <p>Devuelve los documentos y no solo el recuento porque el detector
     * necesita los DNI, para no contar dos veces a la misma persona, y las
     * fechas del primer y ultimo caso.
     */
    @Query("{ anormal: true, zona: ?0, codigoPrueba: ?1, fecha: { $gte: ?2, $lte: ?3 } }")
    List<AnalyticsRecord> casosAnormalesDe(
            String zona, String codigoPrueba, LocalDate desde, LocalDate hasta);

    /**
     * Casos anormales agrupados por zona y prueba en una ventana.
     *
     * <p>Es el barrido del detector de brotes: en lugar de preguntar zona por
     * zona, Mongo devuelve de una vez cada combinacion que llega al umbral.
     * Sin esto, detectar brotes exigiria una consulta por cada par de zona y
     * prueba existente.
     *
     * <p>Cuenta pacientes distintos y no documentos: a una persona a la que se
     * repite la prueba tres veces en la semana no se la puede contar como
     * tres casos.
     */
    @Aggregation(pipeline = {
            """
            { $match: { anormal: true,
                        fecha: { $gte: ?0, $lte: ?1 } } }
            """,
            """
            { $group: { _id: { zona: '$zona', codigoPrueba: '$codigoPrueba' },
                        nombrePrueba: { $first: '$nombrePrueba' },
                        pacientes: { $addToSet: '$dniPaciente' },
                        criticos: { $sum: { $cond: ['$critico', 1, 0] } },
                        primerCaso: { $min: '$fecha' },
                        ultimoCaso: { $max: '$fecha' } } }
            """,
            """
            { $project: { _id: 0,
                          zona: '$_id.zona',
                          codigoPrueba: '$_id.codigoPrueba',
                          nombrePrueba: 1,
                          casos: { $size: '$pacientes' },
                          criticos: 1,
                          dnis: '$pacientes',
                          primerCaso: 1,
                          ultimoCaso: 1 } }
            """,
            "{ $match: { casos: { $gte: ?2 } } }",
            "{ $sort: { casos: -1 } }"
    })
    List<ConteoPorZonaYPrueba> casosPorZonaYPrueba(LocalDate desde, LocalDate hasta, int umbral);

    /**
     * Casos por zona en una ventana, para el mapa de calor.
     *
     * <p>Cuenta documentos y pacientes distintos: el mapa muestra los casos, y
     * el numero de pacientes dice si son muchas personas o una sola a la que
     * se repitio la prueba.
     */
    @Aggregation(pipeline = {
            "{ $match: { anormal: true, fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$zona',
                        total: { $sum: 1 },
                        pacientes: { $addToSet: '$dniPaciente' },
                        criticos: { $sum: { $cond: ['$critico', 1, 0] } } } }
            """,
            """
            { $project: { _id: 0, clave: '$_id', total: 1, criticos: 1,
                          distintos: { $size: '$pacientes' } } }
            """,
            "{ $sort: { total: -1 } }"
    })
    List<ConteoPorClave> casosPorZona(LocalDate desde, LocalDate hasta);

    /** Casos por prueba en una ventana, para saber que se esta detectando. */
    @Aggregation(pipeline = {
            "{ $match: { anormal: true, fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$codigoPrueba',
                        total: { $sum: 1 },
                        pacientes: { $addToSet: '$dniPaciente' },
                        criticos: { $sum: { $cond: ['$critico', 1, 0] } } } }
            """,
            """
            { $project: { _id: 0, clave: '$_id', total: 1, criticos: 1,
                          distintos: { $size: '$pacientes' } } }
            """,
            "{ $sort: { total: -1 } }"
    })
    List<ConteoPorClave> casosPorPrueba(LocalDate desde, LocalDate hasta);

    /**
     * Serie temporal de casos por dia.
     *
     * <p>Agrupa por el campo fecha, que ya viene sin hora: truncar en la
     * agregacion obligaria a convertir zonas horarias dentro de Mongo, y la
     * conversion ya se hizo al registrar el caso, con la zona horaria del
     * servicio.
     */
    @Aggregation(pipeline = {
            "{ $match: { fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$fecha',
                        total: { $sum: 1 },
                        anormales: { $sum: { $cond: ['$anormal', 1, 0] } },
                        criticos: { $sum: { $cond: ['$critico', 1, 0] } } } }
            """,
            "{ $project: { _id: 0, fecha: '$_id', total: 1, anormales: 1, criticos: 1 } }",
            "{ $sort: { fecha: 1 } }"
    })
    List<ConteoPorFecha> serieDiaria(LocalDate desde, LocalDate hasta);

    /** La misma serie, limitada a una zona. */
    @Aggregation(pipeline = {
            "{ $match: { zona: ?2, fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$fecha',
                        total: { $sum: 1 },
                        anormales: { $sum: { $cond: ['$anormal', 1, 0] } },
                        criticos: { $sum: { $cond: ['$critico', 1, 0] } } } }
            """,
            "{ $project: { _id: 0, fecha: '$_id', total: 1, anormales: 1, criticos: 1 } }",
            "{ $sort: { fecha: 1 } }"
    })
    List<ConteoPorFecha> serieDiariaDeZona(LocalDate desde, LocalDate hasta, String zona);

    /** La misma serie, limitada a una prueba. */
    @Aggregation(pipeline = {
            "{ $match: { codigoPrueba: ?2, fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$fecha',
                        total: { $sum: 1 },
                        anormales: { $sum: { $cond: ['$anormal', 1, 0] } },
                        criticos: { $sum: { $cond: ['$critico', 1, 0] } } } }
            """,
            "{ $project: { _id: 0, fecha: '$_id', total: 1, anormales: 1, criticos: 1 } }",
            "{ $sort: { fecha: 1 } }"
    })
    List<ConteoPorFecha> serieDiariaDePrueba(LocalDate desde, LocalDate hasta, String codigoPrueba);

    /**
     * Zonas con registros, para ofrecer el filtro del panel.
     *
     * <p>Con una agregacion y no cargando los documentos con una proyeccion:
     * sobre un ano de resultados eso traeria cientos de miles de objetos a
     * memoria para quedarse con veinte nombres.
     */
    @Aggregation(pipeline = {
            "{ $group: { _id: '$zona' } }",
            "{ $sort: { _id: 1 } }",
            "{ $project: { _id: 0, zona: '$_id' } }"
    })
    List<ZonaProjection> zonasConRegistros();
}
