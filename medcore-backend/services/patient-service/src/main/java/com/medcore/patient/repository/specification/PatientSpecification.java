package com.medcore.patient.repository.specification;

import com.medcore.patient.domain.dto.request.PatientSearchRequest;
import com.medcore.patient.domain.entity.Patient;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye la consulta de busqueda a partir de los criterios recibidos.
 *
 * <p>Se usa Specification en lugar de varios metodos derivados porque los
 * filtros son opcionales y combinables: con seis criterios opcionales harian
 * falta decenas de metodos en el repositorio para cubrir las combinaciones.
 */
public final class PatientSpecification {

    private PatientSpecification() {
        throw new AssertionError("Clase de utilidades, no instanciable");
    }

    public static Specification<Patient> porCriterios(PatientSearchRequest criterios) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (tieneTexto(criterios.texto())) {
                String patron = "%" + criterios.texto().toLowerCase().trim() + "%";
                // Un solo campo de busqueda que cubre DNI, nombres y apellidos:
                // es como busca recepcion, sin saber de antemano cual tiene.
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("dni")), patron),
                        cb.like(cb.lower(root.get("nombres")), patron),
                        cb.like(cb.lower(root.get("apellidos")), patron)));
            }

            if (criterios.sexo() != null) {
                predicados.add(cb.equal(root.get("sexo"), criterios.sexo()));
            }

            if (criterios.estado() != null) {
                predicados.add(cb.equal(root.get("estado"), criterios.estado()));
            }

            if (tieneTexto(criterios.zona())) {
                predicados.add(cb.equal(cb.lower(root.get("zona")),
                        criterios.zona().toLowerCase().trim()));
            }

            // La edad no se almacena, se deriva de la fecha de nacimiento.
            // Filtrar por edad es por tanto filtrar por un rango de fechas,
            // y asi la consulta puede aprovechar el indice.
            if (criterios.edadMin() != null) {
                LocalDate nacidoAntesDe = LocalDate.now().minusYears(criterios.edadMin());
                predicados.add(cb.lessThanOrEqualTo(root.get("fechaNacimiento"), nacidoAntesDe));
            }
            if (criterios.edadMax() != null) {
                LocalDate nacidoDespuesDe = LocalDate.now().minusYears(criterios.edadMax() + 1L);
                predicados.add(cb.greaterThan(root.get("fechaNacimiento"), nacidoDespuesDe));
            }

            return predicados.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
