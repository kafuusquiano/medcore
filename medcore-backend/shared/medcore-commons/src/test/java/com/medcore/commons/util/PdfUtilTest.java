package com.medcore.commons.util;

import com.lowagie.text.pdf.PdfReader;
import com.medcore.commons.util.PdfUtil.Celda;
import com.medcore.commons.util.PdfUtil.Columna;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfUtilTest {

    @Test
    @DisplayName("Genera un PDF valido con todas las piezas")
    void generaUnPdfValido() throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Paciente", "Elena Soto Vargas");
        datos.put("Edad", "37 anios");
        datos.put("Muestra", "MU-20261031-0042");

        byte[] pdf = PdfUtil.documento("MedCore - Laboratorio", lienzo -> lienzo
                .titulo("Informe de laboratorio", "Hemograma completo")
                .datos(datos)
                .seccion("Resultados")
                .tabla(
                        List.of(new Columna("Prueba", 2f),
                                new Columna("Valor", 1f, PdfUtil.Alineacion.DERECHA),
                                new Columna("Referencia", 1.5f)),
                        List.of(
                                List.of(Celda.de("Hemoglobina"), Celda.de("13.4"),
                                        Celda.de("12.0 - 16.0")),
                                List.of(Celda.de("Leucocitos"),
                                        Celda.destacada("14200"),
                                        Celda.de("4000 - 10000"))))
                .nota("Validado por el responsable del area."));

        // Se abre con un lector de PDF: comprobar la firma %PDF solo diria
        // que empieza bien, no que el documento sea legible.
        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(1);
        lector.close();
    }

    @Test
    @DisplayName("Una tabla larga se reparte en varias paginas")
    void tablaLargaSeReparteEnPaginas() throws Exception {
        List<List<Celda>> muchasFilas = IntStream.rangeClosed(1, 120)
                .mapToObj(i -> List.of(Celda.de("Prueba " + i), Celda.de("" + i)))
                .toList();

        byte[] pdf = PdfUtil.documento(null, lienzo -> lienzo
                .titulo("Listado largo", null)
                .tabla(List.of(new Columna("Prueba", 3f), new Columna("Valor", 1f)),
                        muchasFilas));

        // Se lee con el lector y no mirando los bytes: el formato interno del
        // PDF no es parte del contrato de PdfUtil.
        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isGreaterThan(1);
        lector.close();
    }

    @Test
    @DisplayName("Una tabla sin columnas se rechaza")
    void tablaSinColumnasSeRechaza() {
        assertThatThrownBy(() -> PdfUtil.documento(null, lienzo ->
                lienzo.tabla(List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columnas");
    }

    @Test
    @DisplayName("Una fila mas corta que la cabecera se rellena vacia")
    void filaCortaSeRellena() throws Exception {
        // Pasa cuando una prueba no trae rango de referencia. Mejor una celda
        // en blanco que una excepcion que deja al paciente sin informe.
        byte[] pdf = PdfUtil.documento(null, lienzo -> lienzo
                .tabla(List.of(new Columna("A", 1f), new Columna("B", 1f),
                                new Columna("C", 1f)),
                        List.of(List.of(Celda.de("solo una")))));

        assertThat(paginasDe(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("Un valor nulo en una celda no rompe el documento")
    void valorNuloNoRompe() throws Exception {
        byte[] pdf = PdfUtil.documento(null, lienzo -> lienzo
                .datos(new LinkedHashMap<>(Map.of("Clave", "valor")))
                .tabla(List.of(new Columna("X", 1f)),
                        List.of(List.of(Celda.de(null)))));

        assertThat(paginasDe(pdf)).isEqualTo(1);
    }

    private int paginasDe(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        int paginas = lector.getNumberOfPages();
        lector.close();
        return paginas;
    }

    @Test
    @DisplayName("La clase de utilidades no se instancia")
    void noSeInstancia() throws Exception {
        var constructor = PdfUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .hasRootCauseInstanceOf(AssertionError.class);
    }
}
