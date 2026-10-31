package com.medcore.laboratory.service.impl;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.domain.dto.response.SampleResponse;
import com.medcore.laboratory.domain.enums.ResultInterpretation;
import com.medcore.laboratory.domain.enums.SampleStatus;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.service.SampleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabDocumentServiceImplTest {

    @Mock
    private SampleService sampleService;

    private LabDocumentServiceImpl labDocumentService;

    @BeforeEach
    void setUp() {
        labDocumentService = new LabDocumentServiceImpl(sampleService);
    }

    @Test
    @DisplayName("La etiqueta lleva el codigo de la muestra y nada mas")
    void laEtiquetaLlevaSoloElCodigo() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(muestra(SampleStatus.PROCESADA));

        byte[] png = labDocumentService.etiquetaPng(1L, 250);

        // El QR lleva el codigo y no los datos del paciente: una etiqueta se
        // queda pegada en un tubo que pasa por varias manos.
        assertThat(leerQr(png)).isEqualTo("MU-20261031-0042");
        assertThat(leerQr(png)).doesNotContain("Elena");
    }

    @Test
    @DisplayName("La etiqueta se emite en cualquier estado de la muestra")
    void laEtiquetaSeEmiteEnCualquierEstado() throws Exception {
        // Se pega al recibir el tubo, antes de procesarlo: exigir PROCESADA
        // dejaria sin etiqueta justo cuando hace falta.
        when(sampleService.obtenerPorId(1L)).thenReturn(muestra(SampleStatus.REGISTRADA));

        assertThat(leerQr(labDocumentService.etiquetaPng(1L, 200)))
                .isEqualTo("MU-20261031-0042");
    }

    @Test
    @DisplayName("El informe lleva los datos del paciente y sus resultados")
    void elInformeLlevaDatosYResultados() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(muestra(SampleStatus.PROCESADA));

        String texto = textoDe(labDocumentService.informePdf(1L));

        assertThat(texto)
                .contains("Informe de laboratorio")
                .contains("Elena Soto Vargas")
                .contains("71234567")
                .contains("MU-20261031-0042")
                .contains("Hemoglobina")
                .contains("Leucocitos");
    }

    @Test
    @DisplayName("Las pruebas salen ordenadas por codigo, siempre igual")
    void lasPruebasSalenOrdenadas() throws Exception {
        // Dos informes de la misma muestra con las pruebas en distinto orden
        // parecen informes distintos.
        when(sampleService.obtenerPorId(1L)).thenReturn(
                muestraCon(List.of(
                        resultado("HEM-002", "Leucocitos", 14200d,
                                ResultInterpretation.ALTO, false),
                        resultado("HEM-001", "Hemoglobina", 13.4d,
                                ResultInterpretation.NORMAL, false))));

        String texto = textoDe(labDocumentService.informePdf(1L));

        assertThat(texto.indexOf("Hemoglobina")).isLessThan(texto.indexOf("Leucocitos"));
    }

    @Test
    @DisplayName("Un valor critico se anuncia en su propia seccion")
    void elValorCriticoSeAnuncia() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(
                muestraCon(List.of(resultado("HEM-003", "Potasio", 7.2d,
                        ResultInterpretation.CRITICO_ALTO, true))));

        String texto = textoDe(labDocumentService.informePdf(1L));

        assertThat(texto)
                .contains("Valores criticos")
                .contains("ya los ha comunicado al medico");
    }

    @Test
    @DisplayName("Sin valores criticos no se anuncia esa seccion")
    void sinCriticosNoSeAnunciaLaSeccion() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(
                muestraCon(List.of(resultado("HEM-001", "Hemoglobina", 13.4d,
                        ResultInterpretation.NORMAL, false))));

        // Avisar de valores criticos en un informe que no los tiene asusta al
        // paciente sin motivo.
        assertThat(textoDe(labDocumentService.informePdf(1L)))
                .doesNotContain("Valores criticos");
    }

    @Test
    @DisplayName("Una prueba con resultado de texto sale igual que una numerica")
    void resultadoDeTextoSaleIgual() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(
                muestraCon(List.of(new ResultResponse(
                        9L, 1L, "MU-20261031-0042", "MIC-001", "Cultivo de orina",
                        null, "Negativo", null, null, ResultInterpretation.NO_APLICA,
                        "No aplica", false, "MANUAL", "lab01", false, null, null,
                        Instant.parse("2026-10-31T16:00:00Z")))));

        assertThat(textoDe(labDocumentService.informePdf(1L)))
                .contains("Cultivo de orina")
                .contains("Negativo");
    }

    @Test
    @DisplayName("Una muestra en proceso no tiene informe")
    void muestraEnProcesoNoTieneInforme() {
        when(sampleService.obtenerPorId(1L)).thenReturn(muestra(SampleStatus.EN_PROCESO));

        // Un informe con la mitad de las pruebas parece completo: quien lo lea
        // no puede saber que faltan valores.
        assertThatThrownBy(() -> labDocumentService.informePdf(1L))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("MU-20261031-0042")
                .hasMessageContaining("PROCESADA");
    }

    @Test
    @DisplayName("Una muestra rechazada no tiene informe")
    void muestraRechazadaNoTieneInforme() {
        when(sampleService.obtenerPorId(1L)).thenReturn(muestra(SampleStatus.RECHAZADA));

        // Una rechazada no tiene informe, tiene motivo de rechazo.
        assertThatThrownBy(() -> labDocumentService.informePdf(1L))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("RECHAZADA");
    }

    @Test
    @DisplayName("Una muestra procesada sin resultados lo dice en el informe")
    void procesadaSinResultadosLoDice() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(muestraCon(List.of()));

        // No deberia pasar, pero una tabla vacia parece normal y esto no lo es.
        assertThat(textoDe(labDocumentService.informePdf(1L)))
                .contains("no tiene resultados registrados");
    }

    @Test
    @DisplayName("Una muestra urgente lo dice en el subtitulo")
    void muestraUrgenteLoDice() throws Exception {
        when(sampleService.obtenerPorId(1L)).thenReturn(muestra(SampleStatus.PROCESADA));

        assertThat(textoDe(labDocumentService.informePdf(1L)))
                .contains("Muestra urgente");
    }

    // ------------------------------------------------------------------

    private SampleResponse muestra(SampleStatus estado) {
        return new SampleResponse(
                1L, "MU-20261031-0042", 10L, "ORD-20261031-0007",
                3L, "71234567", "Elena Soto Vargas", "FEMENINO", 37,
                5L, "SANGRE", estado, true, estado.admiteResultados(),
                "Ayuno de 8 horas", null, "lab01",
                Instant.parse("2026-10-31T13:00:00Z"),
                estado == SampleStatus.PROCESADA
                        ? Instant.parse("2026-10-31T16:30:00Z") : null,
                List.of(
                        resultado("HEM-001", "Hemoglobina", 13.4d,
                                ResultInterpretation.NORMAL, false),
                        resultado("HEM-002", "Leucocitos", 14200d,
                                ResultInterpretation.ALTO, false)));
    }

    private SampleResponse muestraCon(List<ResultResponse> resultados) {
        return new SampleResponse(
                1L, "MU-20261031-0042", 10L, "ORD-20261031-0007",
                3L, "71234567", "Elena Soto Vargas", "FEMENINO", 37,
                5L, "SANGRE", SampleStatus.PROCESADA, false, false,
                null, null, "lab01",
                Instant.parse("2026-10-31T13:00:00Z"),
                Instant.parse("2026-10-31T16:30:00Z"),
                resultados);
    }

    private ResultResponse resultado(String codigo, String nombre, Double valor,
                                     ResultInterpretation interpretacion,
                                     boolean critico) {
        return new ResultResponse(
                1L, 1L, "MU-20261031-0042", codigo, nombre, valor, null,
                "g/dL", "12.0 - 16.0", interpretacion, interpretacion.name(),
                critico, "MANUAL", "lab01", false, null, null,
                Instant.parse("2026-10-31T16:00:00Z"));
    }

    private String leerQr(byte[] png) throws Exception {
        var imagen = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(imagen)));

        return new QRCodeReader().decode(bitmap).getText();
    }

    /** Extrae el texto del PDF, que es lo que de verdad lee una persona. */
    private String textoDe(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(lector);

        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        lector.close();

        return texto.toString();
    }
}
