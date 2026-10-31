package com.medcore.laboratory.service.impl;

import com.medcore.commons.util.DateUtil;
import com.medcore.commons.util.PdfUtil;
import com.medcore.commons.util.PdfUtil.Alineacion;
import com.medcore.commons.util.PdfUtil.Celda;
import com.medcore.commons.util.PdfUtil.Columna;
import com.medcore.commons.util.QrCodeUtil;
import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.domain.dto.response.SampleResponse;
import com.medcore.laboratory.domain.enums.SampleStatus;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.service.LabDocumentService;
import com.medcore.laboratory.service.SampleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compone la etiqueta y el informe a partir de la muestra y sus resultados.
 *
 * <p>No guarda nada ni toca la base: lee la muestra por el servicio que ya
 * existe y la convierte en papel. Lo que sabe de dominio clinico esta aqui y no
 * en PdfUtil, que solo ofrece las piezas.
 */
@Service
public class LabDocumentServiceImpl implements LabDocumentService {

    /**
     * Las pruebas se ordenan por codigo dentro del informe.
     *
     * <p>Que el orden sea estable importa: dos informes de la misma muestra con
     * las pruebas en distinto orden parecen informes distintos, y el medico que
     * compara el de hoy con el de la semana pasada no deberia tener que buscar
     * cada valor.
     */
    private static final java.util.Comparator<ResultResponse> POR_CODIGO =
            java.util.Comparator.comparing(
                    ResultResponse::codigoPrueba,
                    java.util.Comparator.nullsLast(String::compareTo));

    private final SampleService sampleService;

    public LabDocumentServiceImpl(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @Override
    public byte[] etiquetaPng(Long idMuestra, int lado) {
        SampleResponse muestra = sampleService.obtenerPorId(idMuestra);

        // Lo que va en el QR es solo el codigo, no los datos del paciente: una
        // etiqueta se queda pegada en un tubo que pasa por varias manos, y el
        // nombre del paciente no tiene por que viajar en ella.
        return QrCodeUtil.png(muestra.codigo(), lado);
    }

    @Override
    public byte[] informePdf(Long idMuestra) {
        SampleResponse muestra = sampleService.obtenerPorId(idMuestra);

        if (muestra.estado() != SampleStatus.PROCESADA) {
            throw LaboratoryException.muestraSinInforme(
                    muestra.codigo(), muestra.estado().name());
        }

        List<ResultResponse> resultados = new ArrayList<>(
                muestra.resultados() == null ? List.of() : muestra.resultados());
        resultados.sort(POR_CODIGO);

        String pie = "MedCore - Laboratorio clinico  -  Muestra " + muestra.codigo();

        return PdfUtil.documento(pie, lienzo -> {
            lienzo.titulo("Informe de laboratorio",
                    muestra.urgente() ? "Muestra urgente" : null);
            lienzo.datos(datosDe(muestra));

            lienzo.seccion("Resultados");
            if (resultados.isEmpty()) {
                // No deberia pasar en una muestra PROCESADA, pero si pasa es
                // mejor decirlo que emitir una tabla vacia que parece normal.
                lienzo.texto("La muestra no tiene resultados registrados. "
                        + "Consulte con el laboratorio antes de interpretar "
                        + "este informe.");
            } else {
                lienzo.tabla(columnas(), filasDe(resultados));
            }

            if (resultados.stream().anyMatch(ResultResponse::critico)) {
                lienzo.seccion("Valores criticos");
                lienzo.texto("Este informe contiene valores criticos, marcados "
                        + "en rojo. El laboratorio ya los ha comunicado al "
                        + "medico solicitante.");
            }

            lienzo.nota("Los valores de referencia dependen del sexo y la edad "
                    + "del paciente en la fecha de la toma. Este informe no "
                    + "sustituye a la valoracion del medico.");
        });
    }

    /** Cabecera del informe: a quien pertenece la muestra y cuando se tomo. */
    private Map<String, String> datosDe(SampleResponse muestra) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Paciente", muestra.nombrePaciente());
        datos.put("DNI", muestra.dniPaciente());
        datos.put("Edad", muestra.edadPaciente() == null
                ? "no registrada"
                : muestra.edadPaciente() + " anios");
        datos.put("Sexo", muestra.sexoPaciente() == null
                ? "no registrado"
                : muestra.sexoPaciente());
        datos.put("Muestra", muestra.codigo() + "  (" + muestra.tipoMuestra() + ")");
        datos.put("Orden", muestra.codigoOrden());
        datos.put("Fecha de toma", DateUtil.legible(muestra.fechaToma()));
        datos.put("Fecha de proceso", DateUtil.legible(muestra.fechaProceso()));
        return datos;
    }

    private List<Columna> columnas() {
        return List.of(
                new Columna("Prueba", 2.6f),
                new Columna("Resultado", 1.1f, Alineacion.DERECHA),
                new Columna("Unidad", 0.9f),
                new Columna("Referencia", 1.4f),
                new Columna("Interpretacion", 1.3f));
    }

    private List<List<Celda>> filasDe(List<ResultResponse> resultados) {
        List<List<Celda>> filas = new ArrayList<>(resultados.size());

        for (ResultResponse r : resultados) {
            // Lo fuera de rango sale en rojo y en negrita: quien lo lea tiene
            // que verlo sin buscarlo.
            boolean destacar = r.critico()
                    || (r.interpretacion() != null && r.interpretacion().esAnormal());

            filas.add(List.of(
                    Celda.de(r.nombrePrueba()),
                    new Celda(valorDe(r), destacar),
                    Celda.de(r.unidad()),
                    Celda.de(r.rangoReferencia()),
                    new Celda(etiquetaDe(r), destacar)));
        }

        return filas;
    }

    /**
     * El valor tal como se informa.
     *
     * <p>Una prueba puede dar un numero (hemoglobina 13.4) o un texto
     * (cultivo: negativo). Las dos van en la misma columna, porque en el
     * informe en papel ocupan el mismo sitio.
     */
    private String valorDe(ResultResponse r) {
        if (r.valorTexto() != null && !r.valorTexto().isBlank()) {
            return r.valorTexto();
        }
        return r.valorNumerico() == null ? "" : String.valueOf(r.valorNumerico());
    }

    private String etiquetaDe(ResultResponse r) {
        String etiqueta = r.etiquetaInterpretacion() == null
                ? ""
                : r.etiquetaInterpretacion();

        return r.critico() ? etiqueta + " (critico)" : etiqueta;
    }
}
