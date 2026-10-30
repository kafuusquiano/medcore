package com.medcore.analytics.repository;

import com.medcore.analytics.domain.document.OutbreakAlert;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutbreakAlertRepository extends MongoRepository<OutbreakAlert, String> {

    /**
     * Alerta abierta de una zona y prueba, si la hay.
     *
     * <p>Es lo que impide duplicar avisos del mismo brote: mientras siga
     * abierta, los casos nuevos actualizan sus cifras en lugar de abrir otra.
     * El indice unico parcial de la coleccion lo garantiza en base.
     */
    Optional<OutbreakAlert> findByZonaAndCodigoPruebaAndEstado(
            String zona, String codigoPrueba, AlertStatus estado);

    List<OutbreakAlert> findByEstadoOrderByNivelRiesgoDescFechaDeteccionDesc(AlertStatus estado);

    Page<OutbreakAlert> findAllByOrderByFechaDeteccionDesc(Pageable pageable);

    List<OutbreakAlert> findByZonaOrderByFechaDeteccionDesc(String zona);

    List<OutbreakAlert> findByEstadoAndNivelRiesgoOrderByFechaDeteccionDesc(
            AlertStatus estado, RiskLevel nivelRiesgo);

    /**
     * Alertas detectadas en un rango.
     *
     * <p>Con $gte y $lte explicitos y no con el Between de los metodos
     * derivados: en Spring Data MongoDB, Between es EXCLUSIVO en los dos
     * extremos, al contrario que en JPA, y descartaria las alertas del primer
     * y del ultimo instante del rango.
     */
    @Query(value = "{ fechaDeteccion: { $gte: ?0, $lte: ?1 } }",
            sort = "{ fechaDeteccion: -1 }")
    List<OutbreakAlert> detectadasEntre(Instant desde, Instant hasta);

    long countByEstado(AlertStatus estado);

    long countByEstadoAndNivelRiesgo(AlertStatus estado, RiskLevel nivelRiesgo);
}
