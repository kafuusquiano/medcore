package com.medcore.notification.channel;

import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Envio por correo.
 *
 * <p>En desarrollo apunta a Mailpit, que acepta SMTP sin autenticacion y
 * muestra los mensajes en su interfaz web del puerto 8025. En produccion basta
 * cambiar las variables de entorno del correo.
 *
 * <p>El cuerpo se arma con Thymeleaf cuando el aviso tiene plantilla HTML, y
 * si no se envia como texto plano. Se usa Thymeleaf y no concatenacion de
 * cadenas por dos motivos: cambiar la redaccion no obliga a recompilar, y
 * escapa los datos que vienen de los eventos, que en este servicio son nombres
 * de pacientes y motivos escritos por una persona.
 */
@Component
public class EmailChannel implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    /**
     * Comprobacion basica de la direccion.
     *
     * <p>No pretende validar el estandar completo, que es mucho mas permisivo
     * de lo que parece: solo descarta lo que seguro no es una direccion, para
     * no gastar un intento de envio en algo que el servidor va a rechazar.
     */
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties properties;

    public EmailChannel(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        NotificationProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Override
    public NotificationChannel canal() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public Resultado enviar(Notification aviso, Map<String, String> datos) {
        String destino = aviso.getDestinatario();

        // Sin direccion valida no hay nada que reintentar: el dato no va a
        // aparecer solo en el siguiente intento.
        if (destino == null || destino.isBlank()) {
            return Resultado.falloDefinitivo("El aviso no tiene direccion de correo.");
        }
        if (!CORREO.matcher(destino.trim()).matches()) {
            return Resultado.falloDefinitivo(
                    "La direccion '" + destino + "' no parece un correo valido.");
        }

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mensaje, true, StandardCharsets.UTF_8.name());

            helper.setFrom(properties.remitente(), properties.remitenteNombre());
            helper.setTo(destino.trim());
            helper.setSubject(aviso.getAsunto());

            String html = armarHtml(aviso, datos);
            if (html == null) {
                // Sin plantilla, el texto del aviso sirve como cuerpo. Es el
                // caso de los tipos que nacieron para in-app y acabaron
                // yendo tambien por correo.
                helper.setText(aviso.getMensaje(), false);
            } else {
                // Los dos cuerpos: el cliente que no muestre HTML lee el
                // texto, en lugar de recibir un mensaje vacio.
                helper.setText(aviso.getMensaje(), html);
            }

            mailSender.send(mensaje);

            log.debug("Correo de {} enviado a {}", aviso.getTipo(), destino);
            return Resultado.ok();

        } catch (MailAuthenticationException e) {
            // Credenciales mal puestas: reintentar con las mismas no va a
            // funcionar, pero tampoco es culpa del aviso. Se marca definitivo
            // para no gastar los tres intentos en lo mismo, y queda en el log
            // con nivel ERROR porque es configuracion que hay que arreglar.
            log.error("El servidor de correo rechazo las credenciales: {}", e.getMessage());
            return Resultado.falloDefinitivo(
                    "El servidor de correo rechazo las credenciales del servicio.");

        } catch (MailSendException e) {
            // Servidor caido, red cortada, buzon lleno. Reintentable.
            log.warn("No se pudo enviar el correo de {} a {}: {}",
                    aviso.getTipo(), destino, e.getMessage());
            return Resultado.falloTemporal("Fallo de envio: " + e.getMessage());

        } catch (TemplateInputException e) {
            // Falta el archivo de la plantilla. No se arregla reintentando.
            log.error("Falta la plantilla de correo del aviso {}: {}",
                    aviso.getTipo(), e.getMessage());
            return Resultado.falloDefinitivo("Falta la plantilla de correo: " + e.getMessage());

        } catch (MessagingException | UnsupportedEncodingException e) {
            return Resultado.falloDefinitivo("Mensaje mal formado: " + e.getMessage());

        } catch (RuntimeException e) {
            // Cualquier otro fallo se trata como temporal: es mejor reintentar
            // de mas que descartar un aviso por un error que no se previo.
            log.warn("Fallo inesperado enviando el correo de {}: {}",
                    aviso.getTipo(), e.getMessage());
            return Resultado.falloTemporal("Fallo inesperado: " + e.getMessage());
        }
    }

    /**
     * Arma el HTML con la plantilla del tipo, o devuelve null si no tiene.
     *
     * <p>La plantilla se deduce del tipo de aviso y no se guarda en el
     * registro: el nombre vive en notification_templates, y la dependencia
     * entre el aviso y el archivo queda en un solo sitio.
     */
    private String armarHtml(Notification aviso, Map<String, String> datos) {
        String plantilla = datos == null ? null : datos.get("__plantillaHtml");

        if (plantilla == null || plantilla.isBlank()) {
            return null;
        }

        Context contexto = new Context();
        contexto.setVariable("asunto", aviso.getAsunto());
        contexto.setVariable("mensaje", aviso.getMensaje());
        contexto.setVariable("enlace", aviso.getEnlace());
        contexto.setVariable("remitenteNombre", properties.remitenteNombre());

        // Los datos del evento se exponen como un mapa y no como variables
        // sueltas: asi la plantilla puede anadir un campo nuevo sin tocar esta
        // clase.
        contexto.setVariable("datos", datos);

        return templateEngine.process("email/" + plantilla, contexto);
    }
}
