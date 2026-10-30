package com.medcore.laboratory.service.impl;

import com.medcore.laboratory.domain.entity.CriticalValue;
import com.medcore.laboratory.domain.entity.ReferenceRange;
import com.medcore.laboratory.domain.entity.TestCatalog;
import com.medcore.laboratory.domain.enums.ResultInterpretation;
import com.medcore.laboratory.repository.CriticalValueRepository;
import com.medcore.laboratory.repository.ReferenceRangeRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Interpretacion de los valores medidos.
 *
 * <p>Las consultas son de solo lectura y sobre tablas que casi no cambian. Se
 * dejan sin cache a proposito: el catalogo se corrige a mano cuando el
 * laboratorio ajusta un rango, y una cache obligaria a invalidarla o a
 * esperar un reinicio para que el cambio surta efecto, con el riesgo de
 * interpretar resultados con un rango que ya se sabe equivocado.
 */
@Service
@Transactional(readOnly = true)
public class ValidationServiceImpl implements ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationServiceImpl.class);

    private final ReferenceRangeRepository rangoRepository;
    private final CriticalValueRepository criticoRepository;
    private final TestCatalogRepository catalogoRepository;

    public ValidationServiceImpl(ReferenceRangeRepository rangoRepository,
                                 CriticalValueRepository criticoRepository,
                                 TestCatalogRepository catalogoRepository) {
        this.rangoRepository = rangoRepository;
        this.criticoRepository = criticoRepository;
        this.catalogoRepository = catalogoRepository;
    }

    @Override
    public Interpretacion interpretar(String codigoPrueba, Double valor,
                                      String sexo, Integer edad) {
        if (valor == null) {
            // Prueba cualitativa: su informe es un texto y no hay nada que
            // comparar.
            return Interpretacion.sinRango();
        }

        Optional<ReferenceRange> rango = rangoAplicable(codigoPrueba, sexo, edad);
        String etiqueta = rango.map(r -> etiquetaCon(r, codigoPrueba)).orElse(null);

        // Los limites criticos se comprueban primero: un valor critico esta
        // tambien fuera del rango, y lo que debe informarse es lo mas grave.
        Optional<ResultInterpretation> critica = interpretacionCritica(codigoPrueba, valor);
        if (critica.isPresent()) {
            return new Interpretacion(critica.get(), etiqueta);
        }

        if (rango.isEmpty()) {
            log.debug("La prueba {} no tiene rango aplicable para sexo={} edad={}",
                    codigoPrueba, sexo, edad);
            return Interpretacion.sinRango();
        }

        ReferenceRange aplicable = rango.get();
        ResultInterpretation interpretacion;
        if (aplicable.contiene(valor)) {
            interpretacion = ResultInterpretation.NORMAL;
        } else if (valor < aplicable.getValorMin()) {
            interpretacion = ResultInterpretation.BAJO;
        } else {
            interpretacion = ResultInterpretation.ALTO;
        }

        return new Interpretacion(interpretacion, etiqueta);
    }

    @Override
    public Interpretacion interpretarConFlag(String codigoPrueba, Double valor, String flagHl7,
                                             String sexo, Integer edad) {
        Interpretacion propia = interpretar(codigoPrueba, valor, sexo, edad);

        // La interpretacion propia manda cuando detecta algo critico: los
        // limites del laboratorio son los que valen, no los que trae el
        // analizador configurados de fabrica.
        if (propia.esCritica()) {
            return propia;
        }

        ResultInterpretation delEquipo = ResultInterpretation.desdeHl7(flagHl7);

        // Si el equipo marco critico y el laboratorio no, se respeta el aviso
        // del equipo: dejar pasar un valor peligroso es peor que avisar de
        // mas, y esa discrepancia queda en el log para revisar los limites.
        if (delEquipo.esCritico()) {
            log.warn("El equipo marco {} como {} en la prueba {} pero los limites "
                            + "propios no lo consideran critico. Se respeta el aviso del equipo.",
                    valor, flagHl7, codigoPrueba);
            return new Interpretacion(delEquipo, propia.rangoReferencia());
        }

        // Sin rango propio, el flag del equipo es mejor que nada.
        if (propia.interpretacion() == ResultInterpretation.NO_APLICA
                && delEquipo != ResultInterpretation.NO_APLICA) {
            return new Interpretacion(delEquipo, propia.rangoReferencia());
        }

        return propia;
    }

    /**
     * Elige el rango que corresponde al paciente.
     *
     * <p>Cuando varios aplican gana el mas especifico: el que distingue sexo
     * por encima del generico, y entre dos del mismo nivel el de tramo de
     * edad mas estrecho. Un adolescente de 16 anos encaja tanto en el rango
     * de 15 en adelante como en uno de 0 a 18 si existiera, y el acotado es
     * el que el laboratorio definio pensando en el.
     *
     * <p>Un rango que exige un dato que no se conoce no se aplica. Si falta el
     * sexo se descartan los que lo acotan, y si falta la edad los que acotan
     * edad. Sin esa regla, un paciente del que no se sabe nada, lo que ocurre
     * cuando patient-service no responde, cae en el primer rango generico que
     * encaje: se vio en una muestra de una mujer de 34 anios interpretada
     * contra el rango pediatrico de 11.0 a 14.0 g/dL. El informe mostraba un
     * rango que no era el suyo, lo que es peor que no mostrar ninguno.
     *
     * <p>Cuando no queda ningun rango aplicable el valor se informa sin
     * interpretar, y es el medico quien lo lee con el criterio que
     * corresponda.
     */
    private Optional<ReferenceRange> rangoAplicable(String codigoPrueba, String sexo, Integer edad) {
        List<ReferenceRange> rangos = rangoRepository.findByCodigoPrueba(codigoPrueba);

        return rangos.stream()
                .filter(r -> sexo != null || r.getSexo() == null)
                .filter(r -> edad != null
                        || (r.getEdadMinAnios() == null && r.getEdadMaxAnios() == null))
                .filter(r -> r.aplicaA(sexo, edad))
                .min(Comparator
                        .comparingInt((ReferenceRange r) -> r.getSexo() == null ? 1 : 0)
                        .thenComparingInt(this::amplitudDeEdad));
    }

    /**
     * Amplitud del tramo de edad, para preferir el rango mas acotado.
     * Un extremo sin definir cuenta como abierto hasta 130 anos.
     */
    private int amplitudDeEdad(ReferenceRange rango) {
        int minimo = rango.getEdadMinAnios() == null ? 0 : rango.getEdadMinAnios();
        int maximo = rango.getEdadMaxAnios() == null ? 130 : rango.getEdadMaxAnios();
        return maximo - minimo;
    }

    private Optional<ResultInterpretation> interpretacionCritica(String codigoPrueba, double valor) {
        Optional<CriticalValue> limites = criticoRepository.findByCodigoPrueba(codigoPrueba);
        if (limites.isEmpty()) {
            return Optional.empty();
        }
        CriticalValue critico = limites.get();
        if (critico.esCriticoBajo(valor)) {
            return Optional.of(ResultInterpretation.CRITICO_BAJO);
        }
        if (critico.esCriticoAlto(valor)) {
            return Optional.of(ResultInterpretation.CRITICO_ALTO);
        }
        return Optional.empty();
    }

    /** Anade la unidad del catalogo a la etiqueta del rango, si la tiene. */
    private String etiquetaCon(ReferenceRange rango, String codigoPrueba) {
        String unidad = catalogoRepository.findById(codigoPrueba)
                .map(TestCatalog::getUnidad)
                .orElse(null);
        return unidad == null || unidad.isBlank()
                ? rango.etiqueta()
                : rango.etiqueta() + " " + unidad;
    }
}
