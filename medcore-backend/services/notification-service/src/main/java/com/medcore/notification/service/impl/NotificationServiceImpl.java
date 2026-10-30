package com.medcore.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.dto.response.NotificationResponse;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.entity.NotificationTemplate;
import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.exception.NotificationException;
import com.medcore.notification.mapper.NotificationMapper;
import com.medcore.notification.repository.NotificationRepository;
import com.medcore.notification.repository.NotificationTemplateRepository;
import com.medcore.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationDispatcher dispatcher;
    private final NotificationMapper mapper;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   NotificationTemplateRepository templateRepository,
                                   NotificationDispatcher dispatcher,
                                   NotificationMapper mapper,
                                   NotificationProperties properties,
                                   ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.dispatcher = dispatcher;
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<Notification> crear(NotificationType tipo, String eventId, String destinatario,
                                    Long idPaciente, Long idUsuario,
                                    Map<String, String> datos) {
        return guardar(tipo, eventId, destinatario, idPaciente, idUsuario, datos,
                null, Instant.now());
    }

    @Override
    @Transactional
    public List<Notification> crearPara(NotificationType tipo, String eventId,
                                        String destinatario, Long idPaciente,
                                        Long idUsuario, Map<String, String> datos,
                                        String referencia) {
        return guardar(tipo, eventId, destinatario, idPaciente, idUsuario, datos,
                referencia, Instant.now());
    }

    @Override
    @Transactional
    public List<Notification> programar(NotificationType tipo, String eventId,
                                        String destinatario, Long idPaciente,
                                        Long idUsuario, Map<String, String> datos,
                                        String referencia, Instant cuando) {
        return guardar(tipo, eventId, destinatario, idPaciente, idUsuario, datos,
                referencia, cuando);
    }

    @Override
    @Transactional
    public int cancelarPendientesDe(String referencia, String motivo) {
        if (referencia == null || referencia.isBlank()) {
            return 0;
        }

        List<Notification> cancelables = notificationRepository.cancelablesDe(referencia);
        int cancelados = 0;

        for (Notification aviso : cancelables) {
            try {
                aviso.cancelar(motivo);
                notificationRepository.save(aviso);
                cancelados++;
            } catch (IllegalStateException e) {
                // Salio entre la consulta y aqui. No es un error: ya no
                // hay nada que cancelar.
                log.debug("El aviso {} ya no se puede cancelar: {}",
                        aviso.getId(), e.getMessage());
            }
        }

        if (cancelados > 0) {
            log.info("Cancelados {} aviso(s) pendiente(s) de {}: {}",
                    cancelados, referencia, motivo);
        }

        return cancelados;
    }

    @Override
    public NotificationResponse obtenerPorId(Long id) {
        return mapper.aRespuesta(buscar(id));
    }

    @Override
    public Page<NotificationResponse> bandejaDePaciente(Long idPaciente, Pageable pageable) {
        return notificationRepository.bandejaDePaciente(idPaciente, pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public Page<NotificationResponse> bandejaDeUsuario(Long idUsuario, Pageable pageable) {
        return notificationRepository.bandejaDeUsuario(idUsuario, pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public long sinLeerDePaciente(Long idPaciente) {
        return notificationRepository.countByIdPacienteAndCanalAndEstado(
                idPaciente, NotificationChannel.IN_APP, NotificationStatus.ENVIADA);
    }

    @Override
    public long sinLeerDeUsuario(Long idUsuario) {
        return notificationRepository.countByIdUsuarioAndCanalAndEstado(
                idUsuario, NotificationChannel.IN_APP, NotificationStatus.ENVIADA);
    }

    @Override
    @Transactional
    public NotificationResponse marcarLeido(Long id) {
        Notification aviso = buscar(id);

        if (aviso.getCanal() != NotificationChannel.IN_APP) {
            throw NotificationException.soloInApp(aviso.getCanal().name());
        }

        if (!aviso.getEstado().puedePasarA(NotificationStatus.LEIDA)
                && aviso.getEstado() != NotificationStatus.LEIDA) {
            // Un aviso que nunca se entrego no se puede leer. Marcarlo
            // ocultaria que el usuario no llego a verlo.
            throw NotificationException.transicionInvalida(
                    aviso.getEstado().name(), NotificationStatus.LEIDA.name());
        }

        aviso.marcarLeida();
        return mapper.aRespuesta(notificationRepository.save(aviso));
    }

    @Override
    @Transactional
    public int marcarTodosLeidosDePaciente(Long idPaciente) {
        // Pagina generosa pero acotada: marcar "todos" no debe cargar en
        // memoria el historial completo de un paciente de hace tres anios.
        var sinLeer = notificationRepository.bandejaDePaciente(
                idPaciente, PageRequest.of(0, 200));

        int marcados = 0;
        for (Notification aviso : sinLeer.getContent()) {
            if (aviso.getEstado() == NotificationStatus.ENVIADA) {
                aviso.marcarLeida();
                notificationRepository.save(aviso);
                marcados++;
            }
        }

        return marcados;
    }

    @Override
    public Page<NotificationResponse> listarPorEstado(NotificationStatus estado,
                                                      Pageable pageable) {
        return notificationRepository.findByEstadoOrderByFechaCreacionDesc(estado, pageable)
                .map(mapper::aRespuesta);
    }

    @Override
    public List<NotificationResponse> noEntregados() {
        return notificationRepository.agotados(properties.maxReintentos()).stream()
                .map(mapper::aRespuesta)
                .toList();
    }

    @Override
    public Resumen resumen() {
        return new Resumen(
                notificationRepository.countByEstado(NotificationStatus.PENDIENTE),
                notificationRepository.countByEstado(NotificationStatus.ENVIADA)
                        + notificationRepository.countByEstado(NotificationStatus.LEIDA),
                notificationRepository.countByEstado(NotificationStatus.FALLIDA),
                notificationRepository.countByEstado(NotificationStatus.OMITIDA),
                notificationRepository.contarAgotados(properties.maxReintentos()));
    }

    // ---- Apoyo -------------------------------------------------------------

    /**
     * Crea un aviso por cada canal del tipo y, si no estaba programado para
     * mas tarde, lo entrega en el acto.
     *
     * <p>Los urgentes salen de inmediato y no esperan al proceso periodico:
     * un valor critico avisado media hora despues sirve de poco. Los demas
     * tambien se intentan ya, y el proceso periodico es la red de seguridad
     * para los que fallaron.
     */
    private List<Notification> guardar(NotificationType tipo, String eventId,
                                       String destinatario, Long idPaciente, Long idUsuario,
                                       Map<String, String> datos, String referencia,
                                       Instant cuando) {
        NotificationTemplate plantilla = templateRepository.findById(tipo)
                .filter(NotificationTemplate::isActiva)
                .orElseThrow(() -> NotificationException.plantillaAusente(tipo.name()));

        String asunto = plantilla.asuntoCon(datos);
        String mensaje = plantilla.mensajeCon(datos);
        String enlace = plantilla.enlaceCon(datos);
        String datosJson = serializar(datos, tipo);

        List<Notification> creados = new ArrayList<>();

        for (NotificationChannel canal : tipo.canales()) {
            // La idempotencia se comprueba antes de insertar para no depender
            // de la excepcion, pero el indice unico de V1 es la garantia: dos
            // consumidores a la vez llegarian los dos a esta comprobacion.
            if (notificationRepository.existsByEventIdAndTipoAndCanal(eventId, tipo, canal)) {
                log.debug("El aviso {} de {} por {} ya existia; se ignora el reenvio",
                        eventId, tipo, canal);
                continue;
            }

            String destino = destinoPara(canal, tipo, destinatario, idPaciente, idUsuario);

            // En un aviso INTERNO no se guarda el paciente: el aviso apareceria
            // en SU bandeja, y eso es justo lo que el tipo quiere evitar. Se
            // detecto contra la base real, con el aviso de valor critico
            // visible para el paciente. El nombre del paciente ya va en el
            // texto, que es lo que el medico necesita leer.
            Long pacienteDelAviso = tipo.esParaPaciente() ? idPaciente : null;

            Notification aviso = new Notification(
                    tipo, canal, eventId, destino, pacienteDelAviso, idUsuario,
                    asunto, mensaje, enlace, datosJson, referencia, cuando);

            try {
                creados.add(notificationRepository.save(aviso));
            } catch (DataIntegrityViolationException e) {
                log.debug("El aviso {} de {} por {} lo creo otra ejecucion a la vez",
                        eventId, tipo, canal);
            }
        }

        if (creados.isEmpty()) {
            return creados;
        }

        log.info("Creados {} aviso(s) de {} para el evento {}", creados.size(), tipo, eventId);

        // Los programados para mas tarde los recoge el proceso periodico
        // cuando llegue su hora.
        boolean ahora = !cuando.isAfter(Instant.now());
        if (ahora && (!tipo.esUrgente() || properties.enviarCriticosDeInmediato())) {
            // DESPUES del commit, no aqui mismo. El dispatcher entrega en
            // una transaccion nueva (REQUIRES_NEW), que no ve las filas
            // que esta todavia no ha confirmado: su findById no
            // encontraba el aviso y lo dejaba PENDIENTE sin enviarlo.
            // Se detecto contra la base real, con 17 de 18 avisos
            // pendientes mientras la cola periodica los recogia uno a uno.
            List<Notification> aEntregar = List.copyOf(creados);
            trasCommit(() -> aEntregar.forEach(aviso -> entregarSinRomper(aviso, tipo)));
        }

        return creados;
    }

    /**
     * Ejecuta la entrega cuando la transaccion haya confirmado.
     *
     * <p>Hace falta porque el dispatcher entrega en una transaccion nueva:
     * si se le llamara aqui dentro, no veria el aviso recien insertado y
     * lo dejaria pendiente. Tambien evita enviar un correo de un aviso
     * que un rollback posterior deje sin existir.
     *
     * <p>Sin transaccion activa, como en una prueba unitaria, se ejecuta
     * en el acto.
     */
    private void trasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            accion.run();
                        }
                    });
        } else {
            accion.run();
        }
    }

    /**
     * Serializa los datos del evento para guardarlos con el aviso.
     *
     * <p>Si la serializacion falla, el aviso se crea igual sin ellos:
     * perder las filas de detalle del correo es mucho menos grave que no
     * avisar. El texto del mensaje ya viene resuelto, asi que el aviso
     * sigue siendo legible.
     */
    private String serializar(Map<String, String> datos, NotificationType tipo) {
        if (datos == null || datos.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            log.warn("No se pudieron guardar los datos del aviso de {}: {}. "
                            + "El correo saldra sin las filas de detalle.",
                    tipo, e.getMessage());
            return null;
        }
    }

    /**
     * Entrega el aviso sin dejar que su fallo rompa al consumidor.
     *
     * <p>El aviso ya esta guardado: si el envio falla, queda FALLIDA y el
     * proceso periodico lo reintentara. Propagar el fallo haria que el
     * consumidor de Kafka tratara como error un evento que ya proceso bien.
     */
    private void entregarSinRomper(Notification aviso, NotificationType tipo) {
        try {
            dispatcher.entregar(aviso.getId());
        } catch (RuntimeException e) {
            log.warn("No se pudo entregar en el acto el aviso {} de {}: {}. "
                            + "Queda en cola para el siguiente intento.",
                    aviso.getId(), tipo, e.getMessage());
        }
    }

    /**
     * A donde va el aviso segun el canal.
     *
     * <p>En correo es la direccion; en in-app, el identificador del usuario o
     * del paciente, porque la bandeja se consulta por ese identificador y no
     * por una direccion.
     *
     * <p>Para un tipo INTERNO el destino es siempre el usuario, nunca el
     * paciente: un aviso de valor critico o de muestra rechazada en la bandeja
     * del paciente seria exactamente lo que el tipo quiere evitar.
     */
    private String destinoPara(NotificationChannel canal, NotificationType tipo,
                               String destinatario, Long idPaciente, Long idUsuario) {
        if (canal == NotificationChannel.IN_APP) {
            // El orden depende de a quien va el aviso: en uno INTERNO manda el
            // usuario, aunque el evento traiga tambien un paciente. Al
            // contrario, el aviso del personal acabaria en la bandeja del
            // paciente.
            if (!tipo.esParaPaciente()) {
                return idUsuario != null ? "usuario:" + idUsuario : "desconocido";
            }
            if (idPaciente != null) {
                return "paciente:" + idPaciente;
            }
            if (idUsuario != null) {
                return "usuario:" + idUsuario;
            }
            return "desconocido";
        }

        // Sin direccion, el canal lo marcara como omitido con su motivo. Se
        // crea igual para que quede constancia de que el aviso existia y no
        // pudo salir, en lugar de desaparecer sin rastro.
        return destinatario == null || destinatario.isBlank() ? "" : destinatario;
    }

    private Notification buscar(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> NotificationException.avisoNoEncontrado(id));
    }
}
