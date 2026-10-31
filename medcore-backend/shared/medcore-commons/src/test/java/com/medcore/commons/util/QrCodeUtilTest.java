package com.medcore.commons.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeUtilTest {

    @Test
    @DisplayName("Lo que se codifica se puede volver a leer")
    void loCodificadoSeVuelveALeer() throws Exception {
        // La prueba que importa: que un lector recupere el codigo exacto. Un
        // digito distinto adjunta el resultado al paciente equivocado.
        String codigo = "MU-20261031-0042";

        assertThat(leer(QrCodeUtil.png(codigo))).isEqualTo(codigo);
    }

    @Test
    @DisplayName("Las tildes sobreviven al viaje")
    void lasTildesSobreviven() throws Exception {
        // Sin fijar UTF-8, zxing codifica en ISO-8859-1 y el nombre vuelve
        // roto del lector.
        String conTildes = "Munoz Nunez, Jose Maria - gripe A";

        assertThat(leer(QrCodeUtil.png(conTildes))).isEqualTo(conTildes);
    }

    @Test
    @DisplayName("Genera un PNG de verdad, del tamano pedido")
    void generaUnPngDelTamanoPedido() throws Exception {
        byte[] png = QrCodeUtil.png("MU-1", 200);

        var imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(imagen).isNotNull();
        assertThat(imagen.getWidth()).isEqualTo(200);
        assertThat(imagen.getHeight()).isEqualTo(200);
    }

    @Test
    @DisplayName("El contenido vacio se rechaza")
    void contenidoVacioSeRechaza() {
        // Un QR vacio se genera sin error y produce una etiqueta que no
        // identifica nada: el fallo aparece en el mostrador, no aqui.
        assertThatThrownBy(() -> QrCodeUtil.png(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacio");
        assertThatThrownBy(() -> QrCodeUtil.png("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un tamano absurdo se rechaza")
    void tamanoAbsurdoSeRechaza() {
        assertThatThrownBy(() -> QrCodeUtil.png("MU-1", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 50 y 2000");
        assertThatThrownBy(() -> QrCodeUtil.png("MU-1", 5000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El data URI lleva el PNG en base64")
    void dataUriLlevaElPngEnBase64() throws Exception {
        String uri = QrCodeUtil.dataUri("MU-20261031-0042", 150);

        assertThat(uri).startsWith("data:image/png;base64,");

        // Y lo que lleva dentro sigue siendo el QR legible.
        byte[] png = Base64.getDecoder().decode(
                uri.substring("data:image/png;base64,".length()));
        assertThat(leer(png)).isEqualTo("MU-20261031-0042");
    }

    @Test
    @DisplayName("La clase de utilidades no se instancia")
    void noSeInstancia() throws Exception {
        var constructor = QrCodeUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .hasRootCauseInstanceOf(AssertionError.class);
    }

    /** Decodifica el PNG como lo haria el lector del laboratorio. */
    private String leer(byte[] png) throws Exception {
        var imagen = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(imagen)));

        return new QRCodeReader().decode(bitmap).getText();
    }
}
