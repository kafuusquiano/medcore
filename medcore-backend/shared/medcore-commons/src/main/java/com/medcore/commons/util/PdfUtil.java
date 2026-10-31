package com.medcore.commons.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Documentos PDF, para lo que el paciente se lleva en papel.
 *
 * <p>Ofrece las piezas y no los documentos: titulo, ficha de datos, tabla,
 * nota y pie. Quien sabe que lleva un informe de laboratorio es
 * laboratory-service, no esta clase, y meter aqui el dominio clinico
 * obligaria a tocar medcore-commons cada vez que cambie un informe.
 *
 * <p>El pie se escribe en todas las paginas con el numero y la fecha de
 * emision. Un informe clinico suelto, sin saber de cuando es ni si falta una
 * hoja, no sirve como documento.
 *
 * <p>La dependencia de openpdf esta declarada {@code optional} en
 * medcore-commons: un servicio que llame a esta clase sin declararla falla al
 * arrancar con {@code NoClassDefFoundError}, no al compilar.
 */
public final class PdfUtil {

    private static final Color GRIS_CABECERA = new Color(0xEE, 0xEE, 0xEE);
    private static final Color GRIS_TEXTO = new Color(0x66, 0x66, 0x66);
    private static final Color ROJO_ALERTA = new Color(0xC0, 0x39, 0x2B);

    private static final Font TITULO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SUBTITULO =
            FontFactory.getFont(FontFactory.HELVETICA, 11, GRIS_TEXTO);
    private static final Font SECCION =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font CUERPO =
            FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font CUERPO_FUERTE =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CUERPO_ALERTA =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ROJO_ALERTA);
    private static final Font PIE =
            FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS_TEXTO);

    /** Alineacion de una columna de tabla. */
    public enum Alineacion {
        IZQUIERDA(Element.ALIGN_LEFT),
        CENTRO(Element.ALIGN_CENTER),
        DERECHA(Element.ALIGN_RIGHT);

        private final int valor;

        Alineacion(int valor) {
            this.valor = valor;
        }
    }

    /**
     * Una columna de tabla.
     *
     * @param ancho peso relativo frente a las demas, no una medida absoluta
     */
    public record Columna(String titulo, float ancho, Alineacion alineacion) {

        public Columna(String titulo, float ancho) {
            this(titulo, ancho, Alineacion.IZQUIERDA);
        }
    }

    /**
     * Una celda que puede ir marcada.
     *
     * <p>Lo marcado sale en rojo y en negrita. En un informe de laboratorio es
     * el valor fuera de rango: quien lo lea tiene que verlo sin buscarlo.
     */
    public record Celda(String texto, boolean destacada) {

        public static Celda de(String texto) {
            return new Celda(texto, false);
        }

        public static Celda destacada(String texto) {
            return new Celda(texto, true);
        }
    }

    /**
     * Genera un PDF.
     *
     * @param piePersonalizado texto del pie, a la izquierda del numero de
     *                         pagina; null lo deja solo con la fecha
     * @param contenido        lo que se escribe en el documento
     * @return los bytes del PDF
     * @throws IllegalStateException si el documento no se puede generar
     */
    public static byte[] documento(String piePersonalizado, Consumer<Lienzo> contenido) {
        Document doc = new Document(PageSize.A4, 42, 42, 46, 54);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PieDePagina(piePersonalizado));
            doc.open();
            contenido.accept(new Lienzo(doc));
            doc.close();
            return salida.toByteArray();

        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    /** Las piezas que se pueden escribir en el documento. */
    public static final class Lienzo {

        private final Document doc;

        private Lienzo(Document doc) {
            this.doc = doc;
        }

        /** Titulo del documento, con un subtitulo opcional debajo. */
        public Lienzo titulo(String texto, String subtitulo) {
            escribir(new Paragraph(texto, TITULO));
            if (subtitulo != null && !subtitulo.isBlank()) {
                Paragraph p = new Paragraph(subtitulo, SUBTITULO);
                p.setSpacingAfter(14f);
                escribir(p);
            } else {
                espacio(14f);
            }
            return this;
        }

        /** Encabezado de una seccion. */
        public Lienzo seccion(String texto) {
            Paragraph p = new Paragraph(texto, SECCION);
            p.setSpacingBefore(10f);
            p.setSpacingAfter(5f);
            escribir(p);
            return this;
        }

        /**
         * Ficha de datos en dos columnas, sin bordes.
         *
         * <p>Para la cabecera de un informe: paciente, edad, muestra, fecha. Se
         * conserva el orden del mapa, asi que conviene pasar un LinkedHashMap:
         * en un informe clinico el orden de los datos no es indiferente.
         */
        public Lienzo datos(Map<String, String> pares) {
            PdfPTable tabla = new PdfPTable(new float[]{1f, 2.2f});
            tabla.setWidthPercentage(100);
            tabla.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            tabla.getDefaultCell().setPaddingBottom(3f);

            pares.forEach((clave, valor) -> {
                tabla.addCell(new Phrase(clave, CUERPO_FUERTE));
                tabla.addCell(new Phrase(valor == null ? "" : valor, CUERPO));
            });

            escribir(tabla);
            return this;
        }

        /** Tabla de datos, con cabecera gris que se repite en cada pagina. */
        public Lienzo tabla(List<Columna> columnas, List<List<Celda>> filas) {
            if (columnas.isEmpty()) {
                throw new IllegalArgumentException("Una tabla necesita columnas");
            }

            float[] anchos = new float[columnas.size()];
            for (int i = 0; i < columnas.size(); i++) {
                anchos[i] = columnas.get(i).ancho();
            }

            PdfPTable tabla = new PdfPTable(anchos);
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(4f);
            // La cabecera se repite al partir la tabla: una pagina de valores
            // sin saber de que columna es cada uno no se puede leer.
            tabla.setHeaderRows(1);

            for (Columna c : columnas) {
                PdfPCell celda = new PdfPCell(new Phrase(c.titulo(), CUERPO_FUERTE));
                celda.setBackgroundColor(GRIS_CABECERA);
                celda.setHorizontalAlignment(c.alineacion().valor);
                celda.setPadding(5f);
                tabla.addCell(celda);
            }

            for (List<Celda> fila : filas) {
                for (int i = 0; i < columnas.size(); i++) {
                    Celda dato = i < fila.size() ? fila.get(i) : Celda.de("");
                    String texto = dato.texto() == null ? "" : dato.texto();

                    PdfPCell celda = new PdfPCell(new Phrase(
                            texto, dato.destacada() ? CUERPO_ALERTA : CUERPO));
                    celda.setHorizontalAlignment(columnas.get(i).alineacion().valor);
                    celda.setPadding(4f);
                    tabla.addCell(celda);
                }
            }

            escribir(tabla);
            return this;
        }

        /** Parrafo de texto normal. */
        public Lienzo texto(String texto) {
            escribir(new Paragraph(texto, CUERPO));
            return this;
        }

        /** Nota al pie del contenido, en gris y pequena. */
        public Lienzo nota(String texto) {
            Paragraph p = new Paragraph(texto, PIE);
            p.setSpacingBefore(10f);
            escribir(p);
            return this;
        }

        /** Espacio en blanco vertical. */
        public Lienzo espacio(float alto) {
            Paragraph p = new Paragraph(" ");
            p.setSpacingAfter(alto);
            escribir(p);
            return this;
        }

        /** Empieza una pagina nueva. */
        public Lienzo nuevaPagina() {
            doc.newPage();
            return this;
        }

        private void escribir(Element elemento) {
            try {
                doc.add(elemento);
            } catch (DocumentException e) {
                throw new IllegalStateException(
                        "No se pudo escribir en el PDF", e);
            }
        }
    }

    /** Pie con el texto del documento, la fecha de emision y la pagina. */
    private static final class PieDePagina extends PdfPageEventHelper {

        private final String texto;

        private PieDePagina(String texto) {
            this.texto = texto;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            String izquierda = (texto == null || texto.isBlank() ? "" : texto + "  -  ")
                    + "Emitido el " + DateUtil.enPalabras(LocalDate.now(DateUtil.ZONA_LIMA));

            PdfPTable pie = new PdfPTable(new float[]{3f, 1f});
            pie.setTotalWidth(doc.right() - doc.left());
            pie.getDefaultCell().setBorder(Rectangle.TOP);
            pie.getDefaultCell().setBorderColor(GRIS_CABECERA);
            pie.getDefaultCell().setPaddingTop(4f);

            pie.addCell(new Phrase(izquierda, PIE));

            PdfPCell pagina = new PdfPCell(
                    new Phrase("Pagina " + doc.getPageNumber(), PIE));
            pagina.setBorder(Rectangle.TOP);
            pagina.setBorderColor(GRIS_CABECERA);
            pagina.setPaddingTop(4f);
            pagina.setHorizontalAlignment(Element.ALIGN_RIGHT);
            pie.addCell(pagina);

            pie.writeSelectedRows(0, -1, doc.left(),
                    doc.bottom() - 8, writer.getDirectContent());
        }
    }

    private PdfUtil() {
        throw new AssertionError("Clase de utilidades, no instanciable");
    }
}
