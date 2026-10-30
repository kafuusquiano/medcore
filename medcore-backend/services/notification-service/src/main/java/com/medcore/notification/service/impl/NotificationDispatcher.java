package com.medcore.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medcore.notification.channel.NotificationChannelSender;
import com.medcore.notification.channel.UnsupportedChannel;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.entity.NotificationTemplate;
import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.repository.NotificationRepository;
import com.medcore.notification.repository.NotificationTemplateRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrega los avisos por su canal y registra el desenlace.
 *
 * <p>Es la unica pieza que habla con los canales. Los consumidores solo crean
 * avisos; que salgan, fallen o se reintenten es trabajo de aqui, de modo que
 * la politica de reintentos no quede repetida en nueve consumidores.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    /** Tope de avisos por pasada, para que una cola atascada no monopolice. */
    private static final int LOTE = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final List<NotificationChannelSender> senders;
    private final UnsupportedChannel canalesSinImplementar;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<NotificationChannel, NotificationChannelSender> porCanal =
            new EnumMap<>(NotificationChannel.class);

    public NotificationDispatcher(NotificationRepository notificationRepository,
                                  NotificationTemplateRepository templateRepository,
                                  List<NotificationChannelSender> senders,
                                  UnsupportedChannel canalesSinImplementar,
                                  NotificationProperties properties,
                                  ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.senders = senders;
        this.canalesSinImplementar = canalesSinImplementar;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Indexa los senders por canal al arrancar.
     *
     * <p>Spring inyecta todos los que haya: anadir un canal es escribir una
     * clase, sin tocar esta. Los canales sin implementar se registran despues
     * y no sobrescriben a uno real, de modo que si manana se escribe un sender
     * de PUSH, ese gana.
     */
    @PostConstruct
    void indexarCanales() {
        senders.forEach(sender -> porCanal.put(sender.canal(), sender));

        canalesSinImplementar.senders()
                .forEach(sender -> porCanal.putIfAbsent(sender.canal(), sender));

        log.info("Canales de notificacion disponibles: {}", porCanal.keySet());
    }

    /**
     * Procesa la cola de envio.
     *
     * <p>Cada aviso va en su propia transaccion: si uno falla al guardar su
     * estado, los demas tienen que salir igual. Una pasada que se corta en el
     * tercer aviso deja los cuarenta y siete siguientes sin enviar.
     *
     * @return cuantos se enviaron y cuantos no
     */
    public Resumen procesarCola() {
        List<Notification> cola = notificationRepository.colaDeEnvio(
                Instant.now(), properties.maxReintentos(), PageRequest.of(0, LOTE));

        if (cola.isEmpty()) {
            return new Resumen(0, 0, 0);
        }

        int enviados = 0;
        int fallidos = 0;
        int omitidos = 0;

        for (Notification aviso : cola) {
            try {
                Desenlace desenlace = entregar(aviso.getId());
                switch (desenlace) {
                    case ENVIADO -> enviados++;
                    case FALLIDO -> fallidos++;
                    case OMITIDO -> omitidos++;
                    case NO_PROCESADO -> { }
                }
            } catch (OptimisticLockingFailureException e) {
                // Otra pasada o el consumidor tocaron el aviso a la vez. No es
                // un error: el que llego primero lo esta procesando.
                log.debug("El aviso {} lo esta procesando otra ejecucion", aviso.getId());
            } catch (RuntimeException e) {
                log.error("Error entregando el aviso {}: {}",
                        aviso.getId(), e.getMessage(), e);
                fallidos++;
            }
        }

        log.info("Cola de avisos procesada: {} enviados, {} fallidos, {} omitidos",
                enviados, fallidos, omitidos);

        return new Resumen(enviados, fallidos, omitidos);
    }

    /**
     * Entrega un aviso concreto.
     *
     * <p>En su propia transaccion, con REQUIRES_NEW: el estado del envio tiene
     * que quedar guardado aunque la transaccion de quien llama se deshaga
     * despues. Si no, un aviso podria salir de verdad y quedar en la base como
     * pendiente, y la siguiente pasada lo enviaria otra vez.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Desenlace entregar(Long idAviso) {
        Notification aviso = notificationRepository.findById(idAviso).orElse(null);

        if (aviso == null) {
            return Desenlace.NO_PROCESADO;
        }

        // Pudo enviarlo otra pasada entre que se leyo la cola y se llego aqui.
        if (aviso.getEstado().esFinal()) {
            return Desenlace.NO_PROCESADO;
        }

        NotificationChannelSender sender = porCanal.get(aviso.getCanal());

        if (sender == null) {
            // Un canal declarado en el enum del que nadie registro sender.
            // No deberia ocurrir, pero dejar el aviso pendiente para siempre
            // seria peor que marcarlo y que se vea.
            aviso.marcarOmitida("No hay ningun sender registrado para el canal "
                    + aviso.getCanal() + ".");
            notificationRepository.save(aviso);
            log.error("Sin sender para el canal {}; el aviso {} queda omitido",
                    aviso.getCanal(), aviso.getId());
            return Desenlace.OMITIDO;
        }

        var resultado = sender.enviar(aviso, datosDePlantilla(aviso));

        if (resultado.enviado()) {
            aviso.marcarEnviada();
            notificationRepository.save(aviso);

            if (aviso.getTipo().esUrgente()) {
                // Un aviso urgente se registra con WARN para que destaque: es
                // el que alguien tiene que poder encontrar en el log si hay
                // que demostrar que el sistema aviso, y cuando.
                log.warn("AVISO URGENTE ENVIADO | tipo={} canal={} destinatario={} aviso={}",
                        aviso.getTipo(), aviso.getCanal(), aviso.getDestinatario(),
                        aviso.getId());
            }

            return Desenlace.ENVIADO;
        }

        // Un fallo definitivo no se reintenta: el dato no va a aparecer solo,
        // ni el canal va a configurarse entre un intento y el siguiente.
        if (!resultado.reintentable()) {
            aviso.marcarOmitida(resultado.motivo());
            notificationRepository.save(aviso);
            registrarNoEntregado(aviso, resultado.motivo(), false);
            return Desenlace.OMITIDO;
        }

        aviso.marcarFallida(resultado.motivo());
        notificationRepository.save(aviso);

        boolean agotado = !aviso.puedeReintentarse(properties.maxReintentos());
        registrarNoEntregado(aviso, resultado.motivo(), !agotado);

        return Desenlace.FALLIDO;
    }

    /**
     * Datos que el canal necesita ademas del propio aviso.
     *
     * <p>Hoy es el nombre de la plantilla HTML del correo. No se guarda en el
     * aviso porque ya vive en notification_templates: duplicarlo en cada fila
     * obligaria a migrar los avisos antiguos al renombrar una plantilla.
     *
     * <p>El asunto, el mensaje y el enlace ya vienen resueltos en el aviso, de
     * modo que el canal no tiene que volver a rellenar marcadores.
     */
    private Map<String, String> datosDePlantilla(Notification aviso) {
        Map<String, String> datos = new HashMap<>(deserializar(aviso));

        // El nombre de la plantilla no se guarda en el aviso: vive en
        // notification_templates, y duplicarlo en cada fila obligaria a
        // migrar los avisos antiguos al renombrar una plantilla.
        templateRepository.findById(aviso.getTipo())
                .filter(NotificationTemplate::tienePlantillaHtml)
                .ifPresent(plantilla ->
                        datos.put("__plantillaHtml", plantilla.getPlantillaHtml()));

        return datos;
    }

    /**
     * Recupera los datos del evento guardados con el aviso.
     *
     * <p>Si el JSON esta corrupto, el envio sigue adelante sin ellos: el
     * asunto y el mensaje ya vienen resueltos, asi que el correo es
     * legible aunque le falten las filas de detalle. Descartar el aviso
     * por eso seria desproporcionado.
     */
    private Map<String, String> deserializar(Notification aviso) {
        if (aviso.getDatos() == null || aviso.getDatos().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(aviso.getDatos(),
                    new TypeReference<Map<String, String>>() { });
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Los datos del aviso {} no se pudieron leer: {}. "
                            + "El correo saldra sin las filas de detalle.",
                    aviso.getId(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * Deja constancia de un aviso que no llego.
     *
     * <p>El nivel depende de la gravedad, no del fallo: un recordatorio de
     * cita que no sale es un inconveniente, y un valor critico que no sale
     * exige que alguien coja el telefono. Por eso el segundo se registra con
     * ERROR y texto explicito aunque el fallo sea el mismo.
     */
    private void registrarNoEntregado(Notification aviso, String motivo, boolean quedanIntentos) {
        if (aviso.getTipo().esUrgente() && !quedanIntentos) {
            log.error("AVISO URGENTE NO ENTREGADO | tipo={} canal={} destinatario={} "
                            + "aviso={} intentos={} | {} | Hay que avisar por otro medio.",
                    aviso.getTipo(), aviso.getCanal(), aviso.getDestinatario(),
                    aviso.getId(), aviso.getIntentos(), motivo);
            return;
        }

        if (quedanIntentos) {
            log.warn("Aviso {} de {} no entregado ({} de {} intentos): {}",
                    aviso.getId(), aviso.getTipo(), aviso.getIntentos(),
                    properties.maxReintentos(), motivo);
        } else {
            log.warn("Aviso {} de {} descartado tras {} intentos: {}",
                    aviso.getId(), aviso.getTipo(), aviso.getIntentos(), motivo);
        }
    }

    /** Desenlace de entregar un aviso. */
    public enum Desenlace {
        ENVIADO,
        FALLIDO,
        OMITIDO,
        /** Ya estaba resuelto, o no existe. */
        NO_PROCESADO
    }

    public record Resumen(int enviados, int fallidos, int omitidos) {

        public int total() {
            return enviados + fallidos + omitidos;
        }
    }
}
