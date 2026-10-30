package com.medcore.notification.domain.entity;

import com.medcore.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos de un tipo de aviso.
 *
 * <p>Viven en la base y no en el codigo porque son lo que se corrige con el
 * uso: una redaccion confusa, una falta, un cambio de tono. Ajustarlos no
 * deberia exigir reconstruir la imagen.
 *
 * <p>Aqui solo estan el asunto y el texto corto. El cuerpo HTML de los correos
 * va en {@code resources/templates/email/}, con Thymeleaf: maquetar HTML
 * dentro de una columna de texto seria ilegible, y Thymeleaf ademas escapa los
 * datos que vienen de los eventos.
 *
 * <p>La clave primaria es el tipo: hay exactamente una plantilla por tipo de
 * aviso, y un identificador generado solo anadiria una indireccion.
 */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    /** Marcadores de la forma {nombre}, que se sustituyen al enviar. */
    private static final Pattern MARCADOR = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType tipo;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    /**
     * Plantilla HTML del correo, sin la extension, dentro de
     * {@code templates/email/}. Nula en los tipos que solo van in-app.
     */
    @Column(name = "plantilla_html", length = 80)
    private String plantillaHtml;

    /** Ruta del frontend, con marcadores como {idCita}. */
    @Column(length = 300)
    private String enlace;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    protected NotificationTemplate() {
        // Requerido por JPA.
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    /**
     * Sustituye los marcadores por sus valores.
     *
     * <p>Un marcador sin valor se deja tal cual en lugar de quedar como
     * "null": si el texto sale con un {nombrePaciente} visible, se ve que
     * falta un dato y se corrige. Un "null" en medio de un correo parece un
     * error del sistema y no dice que falta.
     */
    public static String rellenar(String texto, Map<String, String> valores) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }

        Matcher matcher = MARCADOR.matcher(texto);
        StringBuilder resultado = new StringBuilder();

        while (matcher.find()) {
            String clave = matcher.group(1);
            String valor = valores == null ? null : valores.get(clave);
            matcher.appendReplacement(resultado,
                    Matcher.quoteReplacement(valor == null ? matcher.group() : valor));
        }
        matcher.appendTail(resultado);

        return resultado.toString();
    }

    public String asuntoCon(Map<String, String> valores) {
        return rellenar(asunto, valores);
    }

    public String mensajeCon(Map<String, String> valores) {
        return rellenar(mensaje, valores);
    }

    public String enlaceCon(Map<String, String> valores) {
        return rellenar(enlace, valores);
    }

    public boolean tienePlantillaHtml() {
        return plantillaHtml != null && !plantillaHtml.isBlank();
    }

    public NotificationType getTipo() {
        return tipo;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getPlantillaHtml() {
        return plantillaHtml;
    }

    public String getEnlace() {
        return enlace;
    }

    public boolean isActiva() {
        return activa;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationTemplate otra)) {
            return false;
        }
        return tipo == otra.tipo;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tipo);
    }

    @Override
    public String toString() {
        return "NotificationTemplate{" + tipo + "}";
    }
}
