package com.medcore.analytics.repository;

import com.medcore.analytics.domain.document.DiagnosisRecord;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.analytics.repository.projection.ConteoPorZonaYCie10;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas de la vigilancia por codigo CIE-10.
 *
 * <p>Todos los rangos de fecha llevan {@code $gte} y {@code $lte} escritos a
 * mano. En Spring Data MongoDB el {@code Between} derivado del nombre del
 * metodo es EXCLUSIVO en los dos extremos, al contrario que en JPA, y eso deja
 * fuera los casos del primer y del ultimo dia del periodo sin dar ningun error:
 * el informe sale con menos casos y nada avisa.
 */
public interface DiagnosisRecordRepository extends MongoRepository<DiagnosisRecord, String> {

    /**
     * Casos por zona y codigo que alcanzan el umbral, para detectar brotes.
     *
     * <p>Cuenta pacientes DISTINTOS y no diagnosticos: un paciente que vuelve
     * a consulta por lo mismo genera otro diagnostico, y contarlo dos veces
     * inventaria la mitad de un brote.
     *
     * @param umbral casos minimos para que el grupo se devuelva
     */
    @Aggregation(pipeline = {
            "{ $match: { fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: { zona: '$zona', codigo: '$codigoCie10' },
                        descripcion: { $first: '$descripcionCie10' },
                        pacientes: { $addToSet: '$dniPaciente' },
                        notificables: { $sum: { $cond: ['$notificable', 1, 0] } },
                        primerCaso: { $min: '$fecha' },
                        ultimoCaso: { $max: '$fecha' } } }
            """,
            """
            { $project: { _id: 0, zona: '$_id.zona', codigoCie10: '$_id.codigo',
                          descripcionCie10: '$descripcion',
                          casos: { $size: '$pacientes' },
                          notificables: 1, dnis: '$pacientes',
                          primerCaso: 1, ultimoCaso: 1 } }
            """,
            "{ $match: { casos: { $gte: ?2 } } }",
            "{ $sort: { casos: -1 } }"
    })
    List<ConteoPorZonaYCie10> casosPorZonaYCie10(LocalDate desde, LocalDate hasta,
                                                 int umbral);

    /** Los casos de una zona y un codigo, para revisar un brote detectado. */
    @Query("{ zona: ?0, codigoCie10: ?1, fecha: { $gte: ?2, $lte: ?3 } }")
    List<DiagnosisRecord> casosDe(String zona, String codigoCie10,
                                  LocalDate desde, LocalDate hasta);

    /** Casos por zona en el periodo, para el mapa de calor clinico. */
    @Aggregation(pipeline = {
            "{ $match: { fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$zona',
                        total: { $sum: 1 },
                        pacientes: { $addToSet: '$dniPaciente' },
                        criticos: { $sum: { $cond: ['$notificable', 1, 0] } } } }
            """,
            """
            { $project: { _id: 0, clave: '$_id', total: 1, criticos: 1,
                          distintos: { $size: '$pacientes' } } }
            """,
            "{ $sort: { total: -1 } }"
    })
    List<ConteoPorClave> casosPorZona(LocalDate desde, LocalDate hasta);

    /** Los codigos mas diagnosticados del periodo: la morbilidad del centro. */
    @Aggregation(pipeline = {
            "{ $match: { fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$codigoCie10',
                        total: { $sum: 1 },
                        pacientes: { $addToSet: '$dniPaciente' },
                        criticos: { $sum: { $cond: ['$notificable', 1, 0] } } } }
            """,
            """
            { $project: { _id: 0, clave: '$_id', total: 1, criticos: 1,
                          distintos: { $size: '$pacientes' } } }
            """,
            "{ $sort: { total: -1 } }",
            "{ $limit: 20 }"
    })
    List<ConteoPorClave> morbilidad(LocalDate desde, LocalDate hasta);

    /**
     * Casos por grupo de edad, para ver a quien afecta.
     *
     * <p>Es la consulta que distingue una gripe estacional de algo que hay que
     * mirar: la misma cifra de casos concentrada en menores de cinco anios no
     * significa lo mismo que repartida entre adultos.
     */
    @Aggregation(pipeline = {
            "{ $match: { fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$grupoEdad',
                        total: { $sum: 1 },
                        pacientes: { $addToSet: '$dniPaciente' },
                        criticos: { $sum: { $cond: ['$notificable', 1, 0] } } } }
            """,
            """
            { $project: { _id: 0, clave: '$_id', total: 1, criticos: 1,
                          distintos: { $size: '$pacientes' } } }
            """,
            "{ $sort: { total: -1 } }"
    })
    List<ConteoPorClave> casosPorGrupoDeEdad(LocalDate desde, LocalDate hasta);

    /** Casos por dia, para la curva epidemica. */
    @Aggregation(pipeline = {
            "{ $match: { fecha: { $gte: ?0, $lte: ?1 } } }",
            """
            { $group: { _id: '$fecha',
                        total: { $sum: 1 },
                        criticos: { $sum: { $cond: ['$notificable', 1, 0] } } } }
            """,
            "{ $project: { _id: 0, fecha: '$_id', total: 1, criticos: 1 } }",
            "{ $sort: { fecha: 1 } }"
    })
    List<ConteoPorFecha> casosPorDia(LocalDate desde, LocalDate hasta);

    /**
     * Enfermedades de notificacion obligatoria del periodo.
     *
     * <p>Es la lista que hay que mandar al MINSA. Exigen aviso con un solo
     * caso, sin esperar a que se acumule ningun umbral.
     */
    @Query(value = "{ notificable: true, fecha: { $gte: ?0, $lte: ?1 } }",
            sort = "{ fecha: -1 }")
    List<DiagnosisRecord> notificables(LocalDate desde, LocalDate hasta);

    @Query(value = "{ fecha: { $gte: ?0, $lte: ?1 } }", count = true)
    long contarEntre(LocalDate desde, LocalDate hasta);

    @Query(value = "{ notificable: true, fecha: { $gte: ?0, $lte: ?1 } }", count = true)
    long contarNotificablesEntre(LocalDate desde, LocalDate hasta);

    /** Las zonas con algun diagnostico, para los selectores del panel. */
    @Aggregation(pipeline = {
            "{ $group: { _id: '$zona' } }",
            "{ $project: { _id: 0, zona: '$_id' } }",
            "{ $sort: { zona: 1 } }"
    })
    List<com.medcore.analytics.repository.projection.ZonaProjection> zonasConCasos();
}
