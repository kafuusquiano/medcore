package com.medcore.analytics.repository;

import com.medcore.analytics.domain.document.IndicatorSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IndicatorSnapshotRepository extends MongoRepository<IndicatorSnapshot, String> {

    Optional<IndicatorSnapshot> findFirstByIndicadorOrderByPeriodoDesc(String indicador);

    Optional<IndicatorSnapshot> findByIndicadorAndPeriodo(String indicador, LocalDate periodo);

    /**
     * Serie archivada de un indicador.
     *
     * <p>Con $gte y $lte explicitos: el Between de los metodos derivados es
     * EXCLUSIVO en MongoDB, y dejaria fuera la foto del primer y del ultimo
     * dia del rango, que en una serie corta son la mitad de los puntos.
     */
    @Query(value = "{ indicador: ?0, periodo: { $gte: ?1, $lte: ?2 } }",
            sort = "{ periodo: 1 }")
    List<IndicatorSnapshot> fotosEntre(String indicador, LocalDate desde, LocalDate hasta);

    List<IndicatorSnapshot> findByPeriodoOrderByIndicadorAsc(LocalDate periodo);
}
