package com.medcore.laboratory.service.impl;

import com.medcore.laboratory.domain.dto.response.TestCatalogResponse;
import com.medcore.laboratory.domain.entity.CriticalValue;
import com.medcore.laboratory.domain.entity.ReferenceRange;
import com.medcore.laboratory.domain.entity.TestCatalog;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.repository.CriticalValueRepository;
import com.medcore.laboratory.repository.ReferenceRangeRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.service.TestCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TestCatalogServiceImpl implements TestCatalogService {

    private final TestCatalogRepository catalogoRepository;
    private final ReferenceRangeRepository rangoRepository;
    private final CriticalValueRepository criticoRepository;

    public TestCatalogServiceImpl(TestCatalogRepository catalogoRepository,
                                  ReferenceRangeRepository rangoRepository,
                                  CriticalValueRepository criticoRepository) {
        this.catalogoRepository = catalogoRepository;
        this.rangoRepository = rangoRepository;
        this.criticoRepository = criticoRepository;
    }

    @Override
    public List<TestCatalogResponse> listarActivas() {
        return catalogoRepository.findByActivaTrueOrderByAreaAscNombreAsc().stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public List<TestCatalogResponse> listarPorArea(String area) {
        return catalogoRepository.findByAreaAndActivaTrueOrderByNombreAsc(area).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public List<TestCatalogResponse> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return listarActivas();
        }
        return catalogoRepository.buscarPorNombre(texto.trim()).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Override
    public TestCatalogResponse obtenerPorCodigo(String codigo) {
        TestCatalog prueba = catalogoRepository.findById(codigo)
                .orElseThrow(() -> LaboratoryException.pruebaNoEncontrada(codigo));
        return aRespuesta(prueba);
    }

    @Override
    public List<String> areas() {
        return catalogoRepository.areas();
    }

    private TestCatalogResponse aRespuesta(TestCatalog prueba) {
        List<TestCatalogResponse.RangoResponse> rangos =
                rangoRepository.findByCodigoPrueba(prueba.getCodigo()).stream()
                        .map(this::aRespuesta)
                        .toList();

        TestCatalogResponse.LimitesCriticosResponse limites =
                criticoRepository.findByCodigoPrueba(prueba.getCodigo())
                        .map(this::aRespuesta)
                        .orElse(null);

        return new TestCatalogResponse(
                prueba.getCodigo(),
                prueba.getNombre(),
                prueba.getArea(),
                prueba.getUnidad(),
                prueba.getTipoMuestra(),
                prueba.isCuantitativa(),
                prueba.getIndicaciones(),
                prueba.getTiempoEntregaHoras(),
                prueba.isActiva(),
                rangos,
                limites);
    }

    private TestCatalogResponse.RangoResponse aRespuesta(ReferenceRange rango) {
        return new TestCatalogResponse.RangoResponse(
                rango.getSexo(),
                rango.getEdadMinAnios(),
                rango.getEdadMaxAnios(),
                rango.getValorMin(),
                rango.getValorMax(),
                criterioDe(rango));
    }

    private TestCatalogResponse.LimitesCriticosResponse aRespuesta(CriticalValue critico) {
        return new TestCatalogResponse.LimitesCriticosResponse(
                critico.getLimiteInferior(),
                critico.getLimiteSuperior(),
                critico.getIndicacion());
    }

    /**
     * Arma el texto que describe a quien aplica el rango.
     *
     * <p>Se compone aqui y no en el frontend para que el informe en PDF y la
     * pantalla digan lo mismo.
     */
    private String criterioDe(ReferenceRange rango) {
        StringBuilder criterio = new StringBuilder();

        if (rango.getSexo() == null) {
            criterio.append("Cualquier sexo");
        } else {
            criterio.append("MASCULINO".equalsIgnoreCase(rango.getSexo()) ? "Varones" : "Mujeres");
        }

        Integer minima = rango.getEdadMinAnios();
        Integer maxima = rango.getEdadMaxAnios();

        if (minima == null && maxima == null) {
            criterio.append(", cualquier edad");
        } else if (maxima == null) {
            criterio.append(", de ").append(minima).append(" anios en adelante");
        } else if (minima == null) {
            criterio.append(", hasta ").append(maxima).append(" anios");
        } else {
            criterio.append(", de ").append(minima).append(" a ").append(maxima).append(" anios");
        }

        return criterio.toString();
    }
}
