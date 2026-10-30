package com.medcore.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medcore.notification.channel.NotificationChannelSender;
import com.medcore.notification.channel.UnsupportedChannel;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.repository.NotificationRepository;
import com.medcore.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la entrega de avisos.
 *
 * <p>Los canales se sustituyen por dobles manuales y no por mocks: lo que hay
 * que comprobar es como reacciona el dispatcher a cada desenlace, y un doble
 * que devuelve el desenlace que se le pide lo expresa mas claro que un mock
 * con su programacion.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final int MAX_INTENTOS = 3;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    private CanalDoble canalCorreo;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void prepararDispatcher() {
        canalCorreo = new CanalDoble(NotificationChannel.EMAIL);

        dispatcher = new NotificationDispatcher(
                notificationRepository,
                templateRepository,
                List.of(canalCorreo, new CanalDoble(NotificationChannel.IN_APP)),
                new UnsupportedChannel(),
                new NotificationProperties(
                        "no-responder@medcore.local", "MedCore",
                        "laboratorio@medcore.local", MAX_INTENTOS, true, null, null),
                new ObjectMapper());

        dispatcher.indexarCanales();

        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(templateRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Un envio correcto deja el aviso ENVIADA con su hora")
    void unEnvioCorrectoDejaElAvisoEnviada() {
        Notification aviso = aviso(NotificationType.CITA_RESERVADA, NotificationChannel.EMAIL);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));
        canalCorreo.responder(NotificationChannelSender.Resultado.ok());

        var desenlace = dispatcher.entregar(1L);

        assertThat(desenlace).isEqualTo(NotificationDispatcher.Desenlace.ENVIADO);
        assertThat(aviso.getEstado()).isEqualTo(NotificationStatus.ENVIADA);
        assertThat(aviso.getFechaEnvio()).isNotNull();
        assertThat(aviso.getIntentos()).isEqualTo(1);
        assertThat(aviso.getMotivoFallo()).isNull();
    }

    @Test
    @DisplayName("Un fallo temporal deja el aviso FALLIDA y con intentos disponibles")
    void unFalloTemporalDejaElAvisoFallida() {
        Notification aviso = aviso(NotificationType.CITA_RESERVADA, NotificationChannel.EMAIL);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));
        canalCorreo.responder(NotificationChannelSender.Resultado
                .falloTemporal("Servidor de correo inaccesible"));

        var desenlace = dispatcher.entregar(1L);

        assertThat(desenlace).isEqualTo(NotificationDispatcher.Desenlace.FALLIDO);
        assertThat(aviso.getEstado()).isEqualTo(NotificationStatus.FALLIDA);
        assertThat(aviso.getMotivoFallo()).contains("Servidor de correo inaccesible");
        assertThat(aviso.getIntentos()).isEqualTo(1);
        // Le quedan dos: el proceso periodico volvera a intentarlo.
        assertThat(aviso.puedeReintentarse(MAX_INTENTOS)).isTrue();
    }

    @Test
    @DisplayName("Un fallo definitivo deja el aviso OMITIDA y no gasta intentos")
    void unFalloDefinitivoDejaElAvisoOmitida() {
        Notification aviso = aviso(NotificationType.CITA_RESERVADA, NotificationChannel.EMAIL);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));
        canalCorreo.responder(NotificationChannelSender.Resultado
                .falloDefinitivo("La direccion no parece un correo valido"));

        var desenlace = dispatcher.entregar(1L);

        // OMITIDA y no FALLIDA: el dato no va a aparecer solo en el siguiente
        // intento, y contarlo como fallo ensuciaria las cifras de entrega.
        assertThat(desenlace).isEqualTo(NotificationDispatcher.Desenlace.OMITIDO);
        assertThat(aviso.getEstado()).isEqualTo(NotificationStatus.OMITIDA);
        assertThat(aviso.getMotivoFallo()).contains("no parece un correo valido");
        assertThat(aviso.getIntentos()).isZero();
        assertThat(aviso.puedeReintentarse(MAX_INTENTOS)).isFalse();
    }

    @Test
    @DisplayName("Tras agotar los intentos el aviso deja de reintentarse")
    void trasAgotarLosIntentosNoSeReintenta() {
        Notification aviso = aviso(NotificationType.VALOR_CRITICO, NotificationChannel.EMAIL);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));
        canalCorreo.responder(NotificationChannelSender.Resultado.falloTemporal("Red caida"));

        for (int i = 0; i < MAX_INTENTOS; i++) {
            dispatcher.entregar(1L);
        }

        assertThat(aviso.getIntentos()).isEqualTo(MAX_INTENTOS);
        assertThat(aviso.puedeReintentarse(MAX_INTENTOS)).isFalse();
        assertThat(aviso.getEstado()).isEqualTo(NotificationStatus.FALLIDA);
    }

    @Test
    @DisplayName("Un aviso ya resuelto no se vuelve a enviar")
    void unAvisoYaResueltoNoSeVuelveAEnviar() {
        Notification aviso = aviso(NotificationType.CITA_RESERVADA, NotificationChannel.EMAIL);
        aviso.marcarEnviada();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));

        // Pudo enviarlo otra pasada entre que se leyo la cola y se llego aqui.
        var desenlace = dispatcher.entregar(1L);

        assertThat(desenlace).isEqualTo(NotificationDispatcher.Desenlace.NO_PROCESADO);
        assertThat(canalCorreo.llamadas()).isZero();
    }

    @Test
    @DisplayName("Un canal sin implementacion marca el aviso OMITIDA con su motivo")
    void unCanalSinImplementacionOmiteElAviso() {
        Notification aviso = aviso(NotificationType.CITA_RESERVADA, NotificationChannel.PUSH);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));

        var desenlace = dispatcher.entregar(1L);

        // Nada finge funcionar: el panel ve por que no salio el aviso.
        assertThat(desenlace).isEqualTo(NotificationDispatcher.Desenlace.OMITIDO);
        assertThat(aviso.getEstado()).isEqualTo(NotificationStatus.OMITIDA);
        assertThat(aviso.getMotivoFallo()).contains("Firebase");
    }

    @Test
    @DisplayName("El canal SMS tambien queda omitido, con su propio motivo")
    void elCanalSmsQuedaOmitido() {
        Notification aviso = aviso(NotificationType.CITA_RECORDATORIO, NotificationChannel.SMS);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));

        dispatcher.entregar(1L);

        assertThat(aviso.getEstado()).isEqualTo(NotificationStatus.OMITIDA);
        assertThat(aviso.getMotivoFallo()).contains("pasarela de mensajeria");
    }

    @Test
    @DisplayName("Un aviso que no existe no rompe la pasada")
    void unAvisoQueNoExisteNoRompe() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(dispatcher.entregar(99L))
                .isEqualTo(NotificationDispatcher.Desenlace.NO_PROCESADO);
    }

    @Test
    @DisplayName("La cola procesa todos los avisos aunque uno falle")
    void laColaProcesaTodosAunqueUnoFalle() {
        Notification primero = aviso(NotificationType.CITA_RESERVADA,
                NotificationChannel.EMAIL, 1L);
        Notification segundo = aviso(NotificationType.CITA_RESERVADA,
                NotificationChannel.EMAIL, 2L);
        Notification tercero = aviso(NotificationType.CITA_RESERVADA,
                NotificationChannel.EMAIL, 3L);

        when(notificationRepository.colaDeEnvio(any(), anyInt(), any()))
                .thenReturn(List.of(primero, segundo, tercero));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(primero));
        // El segundo revienta al leerse.
        when(notificationRepository.findById(2L))
                .thenThrow(new IllegalStateException("base inaccesible"));
        when(notificationRepository.findById(3L)).thenReturn(Optional.of(tercero));

        canalCorreo.responder(NotificationChannelSender.Resultado.ok());

        var resumen = dispatcher.procesarCola();

        // Una pasada que se corta en el segundo aviso deja los demas sin
        // enviar, y en esta cola puede haber un valor critico.
        assertThat(resumen.enviados()).isEqualTo(2);
        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(primero.getEstado()).isEqualTo(NotificationStatus.ENVIADA);
        assertThat(tercero.getEstado()).isEqualTo(NotificationStatus.ENVIADA);
    }

    @Test
    @DisplayName("Una cola vacia no hace nada")
    void unaColaVaciaNoHaceNada() {
        when(notificationRepository.colaDeEnvio(any(), anyInt(), any()))
                .thenReturn(List.of());

        var resumen = dispatcher.procesarCola();

        assertThat(resumen.total()).isZero();
        assertThat(canalCorreo.llamadas()).isZero();
    }

    @Test
    @DisplayName("Los datos del evento llegan al canal para la plantilla")
    void losDatosDelEventoLleganAlCanal() {
        Notification aviso = aviso(NotificationType.VALOR_CRITICO, NotificationChannel.EMAIL);
        ReflectionTestUtils.setField(aviso, "datos",
                "{\"nombrePaciente\":\"PEREZ, Maria\",\"valor\":\"6.9\"}");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));
        canalCorreo.responder(NotificationChannelSender.Resultado.ok());

        dispatcher.entregar(1L);

        // Sin esto, un reintento tras reiniciar el servicio enviaria el correo
        // con los huecos de la plantilla vacios.
        assertThat(canalCorreo.ultimosDatos()).containsEntry("nombrePaciente", "PEREZ, Maria");
        assertThat(canalCorreo.ultimosDatos()).containsEntry("valor", "6.9");
    }

    @Test
    @DisplayName("Un JSON de datos corrupto no impide el envio")
    void unJsonCorruptoNoImpideElEnvio() {
        Notification aviso = aviso(NotificationType.CITA_RESERVADA, NotificationChannel.EMAIL);
        ReflectionTestUtils.setField(aviso, "datos", "{esto no es json");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(aviso));
        canalCorreo.responder(NotificationChannelSender.Resultado.ok());

        var desenlace = dispatcher.entregar(1L);

        // El asunto y el mensaje ya vienen resueltos, asi que el correo es
        // legible aunque le falten las filas de detalle. Descartar el aviso
        // por eso seria desproporcionado.
        assertThat(desenlace).isEqualTo(NotificationDispatcher.Desenlace.ENVIADO);
        assertThat(canalCorreo.ultimosDatos()).isEmpty();
    }

    @Test
    @DisplayName("El tope de reintentos invalido cae al valor por omision")
    void elTopeInvalidoCaeAlValorPorOmision() {
        // Un tope de 0 dejaria todos los avisos sin enviar.
        assertThat(new NotificationProperties(null, null, null, 0, null, null, null)
                .maxReintentos()).isEqualTo(3);
        assertThat(new NotificationProperties(null, null, null, null, null, null, null)
                .maxReintentos()).isEqualTo(3);
        assertThat(new NotificationProperties(null, null, null, 5, null, null, null)
                .maxReintentos()).isEqualTo(5);
    }

    @Test
    @DisplayName("El remitente y el buzon interno tienen valor por omision")
    void elRemitenteTieneValorPorOmision() {
        var vacias = new NotificationProperties(null, null, "  ", null, null, null, null);

        // Sin estos respaldos, un arranque con el config-server a medias
        // dejaria cada envio fallando con NullPointerException.
        assertThat(vacias.remitente()).isEqualTo("no-responder@medcore.local");
        assertThat(vacias.remitenteNombre()).isEqualTo("MedCore");
        assertThat(vacias.correoInterno()).isEqualTo("laboratorio@medcore.local");
        assertThat(vacias.enviarCriticosDeInmediato()).isTrue();
    }

    // ---- Doble de canal ----------------------------------------------------

    /**
     * Canal que devuelve el desenlace que se le indique.
     *
     * <p>Se escribe a mano en lugar de usar un mock porque lo que se prueba es
     * la reaccion del dispatcher a cada desenlace, y asi el test dice
     * "responde esto" en lugar de programar un mock.
     */
    private static final class CanalDoble implements NotificationChannelSender {

        private final NotificationChannel canal;
        private final List<Map<String, String>> recibidos = new ArrayList<>();
        private Resultado respuesta = Resultado.ok();

        private CanalDoble(NotificationChannel canal) {
            this.canal = canal;
        }

        void responder(Resultado respuesta) {
            this.respuesta = respuesta;
        }

        int llamadas() {
            return recibidos.size();
        }

        Map<String, String> ultimosDatos() {
            return recibidos.isEmpty() ? Map.of() : recibidos.get(recibidos.size() - 1);
        }

        @Override
        public NotificationChannel canal() {
            return canal;
        }

        @Override
        public Resultado enviar(Notification aviso, Map<String, String> datos) {
            recibidos.add(datos);
            return respuesta;
        }
    }

    // ---- Datos de prueba ---------------------------------------------------

    private Notification aviso(NotificationType tipo, NotificationChannel canal) {
        return aviso(tipo, canal, 1L);
    }

    private Notification aviso(NotificationType tipo, NotificationChannel canal, Long id) {
        Notification aviso = new Notification(
                tipo, canal, "evt-" + id, "paciente@correo.local",
                3L, null, "Asunto de prueba", "Mensaje de prueba",
                "/ruta", null, Instant.now());
        ReflectionTestUtils.setField(aviso, "id", id);
        return aviso;
    }
}
