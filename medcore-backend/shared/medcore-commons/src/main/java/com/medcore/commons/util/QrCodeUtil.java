package com.medcore.commons.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Codigos QR, para las etiquetas de las muestras.
 *
 * <p>El tecnico escanea la etiqueta en lugar de teclear el codigo. Un digito
 * mal teclado en un codigo de muestra adjunta un resultado al paciente
 * equivocado, y ese es el error mas grave que puede cometer un laboratorio.
 *
 * <p>Las dependencias de zxing estan declaradas {@code optional} en
 * medcore-commons: un servicio que llame a esta clase sin declararlas falla al
 * arrancar con {@code NoClassDefFoundError}, no al compilar.
 */
public final class QrCodeUtil {

    /** Lado del PNG en pixeles. 300 imprime limpio en una etiqueta de 5 cm. */
    public static final int LADO_POR_DEFECTO = 300;

    /**
     * Correccion de errores alta: recupera el contenido con hasta el 30 % del
     * codigo danado.
     *
     * <p>Es lo que corresponde aqui. Una etiqueta de laboratorio se moja, se
     * roza con el guante y se pega sobre un tubo curvo; con el nivel bajo, un
     * borron deja la muestra sin identificar.
     */
    private static final ErrorCorrectionLevel CORRECCION = ErrorCorrectionLevel.H;

    /** Margen en modulos. El estandar pide 4; menos y algunos lectores fallan. */
    private static final int MARGEN = 2;

    /**
     * Genera un QR en PNG.
     *
     * @param contenido lo que lleva codificado, por ejemplo el codigo de la
     *                  muestra MU-20261031-0042
     * @param lado      ancho y alto en pixeles
     * @return los bytes del PNG
     * @throws IllegalArgumentException si el contenido esta vacio: un QR vacio
     *                                 se genera sin error y produce una
     *                                 etiqueta que no identifica nada
     * @throws IllegalStateException    si zxing no puede codificarlo
     */
    public static byte[] png(String contenido, int lado) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException(
                    "El contenido del QR no puede estar vacio");
        }
        if (lado < 50 || lado > 2000) {
            throw new IllegalArgumentException(
                    "El lado del QR debe estar entre 50 y 2000 pixeles, recibido " + lado);
        }

        Map<EncodeHintType, Object> pistas = new EnumMap<>(EncodeHintType.class);
        pistas.put(EncodeHintType.ERROR_CORRECTION, CORRECCION);
        pistas.put(EncodeHintType.MARGIN, MARGEN);
        // Sin esto, zxing codifica en ISO-8859-1 y un nombre con tilde se lee
        // mal en el lector.
        pistas.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

        try {
            BitMatrix matriz = new QRCodeWriter()
                    .encode(contenido, BarcodeFormat.QR_CODE, lado, lado, pistas);

            BufferedImage imagen = MatrixToImageWriter.toBufferedImage(matriz);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "PNG", salida);
            return salida.toByteArray();

        } catch (WriterException e) {
            throw new IllegalStateException(
                    "No se pudo codificar el QR de '" + contenido + "'", e);
        } catch (IOException e) {
            // ByteArrayOutputStream no hace E/S de verdad, asi que esto no
            // deberia ocurrir nunca.
            throw new IllegalStateException("No se pudo escribir el PNG del QR", e);
        }
    }

    /** El QR con el lado por defecto. */
    public static byte[] png(String contenido) {
        return png(contenido, LADO_POR_DEFECTO);
    }

    /**
     * El QR como data URI, para incrustarlo en un HTML o un correo.
     *
     * <p>Evita publicar un endpoint de imagen solo para la etiqueta, y que el
     * cliente de correo tenga que ir a buscarla: muchos bloquean las imagenes
     * remotas y la etiqueta saldria en blanco.
     */
    public static String dataUri(String contenido, int lado) {
        return "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(png(contenido, lado));
    }

    /** El data URI con el lado por defecto. */
    public static String dataUri(String contenido) {
        return dataUri(contenido, LADO_POR_DEFECTO);
    }

    private QrCodeUtil() {
        throw new AssertionError("Clase de utilidades, no instanciable");
    }
}
