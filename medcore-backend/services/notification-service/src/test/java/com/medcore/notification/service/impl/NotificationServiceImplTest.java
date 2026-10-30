package com.medcore.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medcore.notification.config.NotificationProperties;
import com.medcore.notification.domain.entity.Notification;
import com.medcore.notification.domain.entity.NotificationTemplate;
import com.medcore.notification.domain.enums.NotificationChannel;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.domain.enums.NotificationType;
import com.medcore.notification.exception.NotificationException;
import com.medcore.notification.mapper.NotificationMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationDispatcher dispatcher;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void prepararServicio() {
        NotificationProperties properties = new NotificationProperties(
                "no-responder@medcore.local", "MedCore", "laboratorio@medcore.local",
                3, true, null, null);

        notificationService = new NotificationServiceImpl(
                notificationRepository, templateRepository, dispatcher,
                new NotificationMapper(properties), properties, new ObjectMapper());

        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocacion -> {
                    Notification aviso = invocacion.getArgument(0);
                    if (aviso.getId() == null) {
                        ReflectionTestUtils.setField(aviso, "id", 100L);
                    }
                    return aviso;
                });
    }

    @Test
    @DisplayName("Crea un aviso por cada canal del tipo")
    void creaUnAvisoPorCadaCanal() {
        when(templateRepository.findById(NotificationType.CITA_RESERVADA))
                .thenReturn(Optional.of(plantilla(NotificationType.CITA_RESERVADA,
                        "Cita del {fecha}", "Su cita es el {fecha} a las {hora}.",
                        "appointment-confirmed", "/mis-citas/{idCita}")));
        when(notificationRepository.existsByEventIdAndTipoAndCanal(anyString(), any(), any()))
                .thenReturn(false);

        var creados = notificationService.crear(
                NotificationType.CITA_RESERVADA, "evt-1", "paciente@correo.local",
                3L, null, Map.of("fecha", "15/11/2026", "hora", "09:30", "idCita", "42"));

        // CITA_RESERVADA va por correo y tambien in-app: dos avisos, cada uno
        // con su propio estado de envio.
        assertThat(creados).hasSize(2);
        assertThat(creados).extracting(Notification::getCanal)
                .containsExactlyInAnyOrder(
                        NotificationChannel.EMAIL, NotificationChannel.IN_APP);
    }

    @Test
    @DisplayName("Rellena los marcadores de la plantilla")
    void rellenaLosMarcadores() {
        when(templateRepository.findById(NotificationType.CITA_RESERVADA))
                .thenReturn(Optional.of(plantilla(NotificationType.CITA_RESERVADA,
                        "Cita del {fecha}", "Su cita es el {fecha} a las {hora}.",
                        null, "/mis-citas/{idCita}")));

        var creados = notificationService.crear(
                NotificationType.CITA_RESERVADA, "evt-2", "paciente@correo.local",
                3L, null, Map.of("fecha", "15/11/2026", "hora", "09:30", "idCita", "42"));

        Notification aviso = creados.get(0);
        assertThat(aviso.getAsunto()).isEqualTo("Cita del 15/11/2026");
        assertThat(aviso.getMensaje()).isEqualTo("Su cita es el 15/11/2026 a las 09:30.");
        assertThat(aviso.getEnlace()).isEqualTo("/mis-citas/42");
    }

    @Test
    @DisplayName("Un marcador sin valor queda visible, no como null")
    void unMarcadorSinValorQuedaVisible() {
        when(templateRepository.findById(NotificationType.CITA_RESERVADA))
                .thenReturn(Optional.of(plantilla(NotificationType.CITA_RESERVADA,
                        "Cita", "Su cita con {nombreMedico} el {fecha}.", null, null)));

        var creados = notificationService.crear(
                NotificationType.CITA_RESERVADA, "evt-3", "paciente@correo.local",
                3L, null, Map.of("fecha", "15/11/2026"));

        // Un "null" en medio de un correo parece un error del sistema y no
        // dice que falta; el marcador visible si.
        assertThat(creados.get(0).getMensaje())
                .isEqualTo("Su cita con {nombreMedico} el 15/11/2026.");
    }

    @Test
    @DisplayName("Un reenvio del mismo evento no crea avisos duplicados")
    void unReenvioNoCreaDuplicados() {
        when(templateRepository.findById(NotificationType.PACIENTE_REGISTRADO))
                .thenReturn(Optional.of(plantilla(NotificationType.PACIENTE_REGISTRADO,
                        "Bienvenido", "Hola {nombrePaciente}.", "welcome", null)));
        // Kafka entrega al menos una vez: sin esto, el paciente recibiria el
        // correo de bienvenida dos veces.
        when(notificationRepository.existsByEventIdAndTipoAndCanal(
                "evt-4", NotificationType.PACIENTE_REGISTRADO, NotificationChannel.EMAIL))
                .thenReturn(true);

        var creados = notificationService.crear(
                NotificationType.PACIENTE_REGISTRADO, "evt-4", "paciente@correo.local",
                3L, null, Map.of("nombrePaciente", "PEREZ, Maria"));

        assertThat(creados).isEmpty();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin plantilla activa no se puede crear el aviso")
    void sinPlantillaNoSeCreaElAviso() {
        when(templateRepository.findById(NotificationType.VALOR_CRITICO))
                .thenReturn(Optional.empty());

        // Las carga la migracion V1, asi que esto solo ocurre si alguien la
        // borro o desactivo a mano.
        assertThatThrownBy(() -> notificationService.crear(
                NotificationType.VALOR_CRITICO, "evt-5", "lab@medcore.local",
                3L, 9L, Map.of()))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("No hay plantilla activa");
    }

    @Test
    @DisplayName("Una plantilla desactivada cuenta como ausente")
    void unaPlantillaDesactivadaCuentaComoAusente() {
        NotificationTemplate desactivada = plantilla(NotificationType.CITA_RESERVADA,
                "Cita", "Mensaje", null, null);
        ReflectionTestUtils.setField(desactivada, "activa", false);
        when(templateRepository.findById(NotificationType.CITA_RESERVADA))
                .thenReturn(Optional.of(desactivada));

        assertThatThrownBy(() -> notificationService.crear(
                NotificationType.CITA_RESERVADA, "evt-6", "paciente@correo.local",
                3L, null, Map.of()))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("No hay plantilla activa");
    }

    @Test
    @DisplayName("El destino in-app es el identificador, no una direccion")
    void elDestinoInAppEsElIdentificador() {
        when(templateRepository.findById(NotificationType.MUESTRA_REGISTRADA))
                .thenReturn(Optional.of(plantilla(NotificationType.MUESTRA_REGISTRADA,
                        "Muestra recibida", "Su muestra {codigoMuestra} llego.", null, null)));

        var creados = notificationService.crear(
                NotificationType.MUESTRA_REGISTRADA, "evt-7", null,
                3L, null, Map.of("codigoMuestra", "MUE-20261030-0001"));

        // La bandeja se consulta por identificador, no por direccion.
        assertThat(creados).hasSize(1);
        assertThat(creados.get(0).getCanal()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(creados.get(0).getDestinatario()).isEqualTo("paciente:3");
    }

    @Test
    @DisplayName("Un aviso interno NO acaba en la bandeja del paciente")
    void unAvisoInternoNoAcabaEnLaBandejaDelPaciente() {
        when(templateRepository.findById(NotificationType.VALOR_CRITICO))
                .thenReturn(Optional.of(plantilla(NotificationType.VALOR_CRITICO,
                        "VALOR CRITICO en {nombrePaciente}",
                        "El paciente {nombrePaciente} presenta un valor critico.",
                        "critical-value", null)));

        // El evento trae los dos: el paciente al que se refiere y el medico al
        // que hay que avisar.
        var creados = notificationService.crear(
                NotificationType.VALOR_CRITICO, "evt-critico", "lab@medcore.local",
                3L, 9L, Map.of("nombrePaciente", "PEREZ, Maria"));

        Notification inApp = creados.stream()
                .filter(aviso -> aviso.getCanal() == NotificationChannel.IN_APP)
                .findFirst()
                .orElseThrow();

        // Se detecto contra la base real: el aviso de valor critico aparecia
        // en la bandeja del PACIENTE, que es justo lo que el tipo quiere
        // evitar. El nombre del paciente va en el texto, para el medico.
        assertThat(inApp.getIdPaciente()).isNull();
        assertThat(inApp.getIdUsuario()).isEqualTo(9L);
        assertThat(inApp.getDestinatario()).isEqualTo("usuario:9");
        assertThat(inApp.getMensaje()).contains("PEREZ, Maria");
    }

    @Test
    @DisplayName("Un aviso para el paciente si lleva su identificador")
    void unAvisoParaElPacienteLlevaSuIdentificador() {
        when(templateRepository.findById(NotificationType.RESULTADO_LISTO))
                .thenReturn(Optional.of(plantilla(NotificationType.RESULTADO_LISTO,
                        "Resultado disponible", "Su resultado ya esta disponible.",
                        "result-ready", null)));

        var creados = notificationService.crear(
                NotificationType.RESULTADO_LISTO, "evt-resultado", "paciente@correo.local",
                3L, 9L, Map.of());

        Notification inApp = creados.stream()
                .filter(aviso -> aviso.getCanal() == NotificationChannel.IN_APP)
                .findFirst()
                .orElseThrow();

        assertThat(inApp.getIdPaciente()).isEqualTo(3L);
        assertThat(inApp.getDestinatario()).isEqualTo("paciente:3");
    }

    @Test
    @DisplayName("Los avisos del almacen van solo por correo")
    void losAvisosDelAlmacenVanSoloPorCorreo() {
        // Los eventos de inventario no traen usuario ni paciente, asi que un
        // aviso in-app no tendria bandeja donde aparecer: se creaba y quedaba
        // siempre OMITIDO.
        assertThat(NotificationType.STOCK_BAJO.canales())
                .containsExactly(NotificationChannel.EMAIL);
        assertThat(NotificationType.LOTE_VENCIDO.canales())
                .containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("Un aviso programado no se entrega en el acto")
    void unAvisoProgramadoNoSeEntregaEnElActo() {
        when(templateRepository.findById(NotificationType.CITA_RECORDATORIO))
                .thenReturn(Optional.of(plantilla(NotificationType.CITA_RECORDATORIO,
                        "Recordatorio", "Su cita es manana.", "appointment-reminder", null)));

        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        var creados = notificationService.programar(
                NotificationType.CITA_RECORDATORIO, "evt-8:recordatorio",
                "paciente@correo.local", 3L, null, Map.of(), manana);

        assertThat(creados).hasSize(1);
        assertThat(creados.get(0).getFechaProgramada()).isEqualTo(manana);
        assertThat(creados.get(0).estaLista(Instant.now())).isFalse();
        // Lo recogera la pasada periodica cuando llegue su hora.
        verify(dispatcher, never()).entregar(anyLong());
    }

    @Test
    @DisplayName("Un aviso de ahora se intenta entregar de inmediato")
    void unAvisoDeAhoraSeEntregaDeInmediato() {
        when(templateRepository.findById(NotificationType.VALOR_CRITICO))
                .thenReturn(Optional.of(plantilla(NotificationType.VALOR_CRITICO,
                        "VALOR CRITICO", "Valor critico en {nombrePaciente}.",
                        "critical-value", null)));

        notificationService.crear(
                NotificationType.VALOR_CRITICO, "evt-9:critico", "lab@medcore.local",
                3L, 9L, Map.of("nombrePaciente", "PEREZ, Maria"));

        // Un valor critico avisado media hora despues sirve de poco.
        verify(dispatcher, org.mockito.Mockito.times(2)).entregar(anyLong());
    }

    @Test
    @DisplayName("Un fallo al entregar no rompe la creacion")
    void unFalloAlEntregarNoRompeLaCreacion() {
        when(templateRepository.findById(NotificationType.CITA_RESERVADA))
                .thenReturn(Optional.of(plantilla(NotificationType.CITA_RESERVADA,
                        "Cita", "Mensaje", null, null)));
        when(dispatcher.entregar(anyLong()))
                .thenThrow(new IllegalStateException("correo caido"));

        // El aviso ya esta guardado: si el envio falla, queda en cola. Propagar
        // haria que el consumidor de Kafka tratara como error un evento que ya
        // proceso bien.
        var creados = notificationService.crear(
                NotificationType.CITA_RESERVADA, "evt-10", "paciente@correo.local",
                3L, null, Map.of());

        assertThat(creados).hasSize(2);
    }

    @Test
    @DisplayName("Marcar leido exige que el aviso sea in-app")
    void marcarLeidoExigeInApp() {
        Notification correo = aviso(NotificationChannel.EMAIL);
        correo.marcarEnviada();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(correo));

        // El servicio no sabe si el destinatario abrio un correo, y fingir que
        // lo sabe daria una cifra falsa.
        assertThatThrownBy(() -> notificationService.marcarLeido(1L))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("no se puede marcar como leido");
    }

    @Test
    @DisplayName("Marcar leido funciona sobre un aviso in-app enviado")
    void marcarLeidoFuncionaSobreInApp() {
        Notification inApp = aviso(NotificationChannel.IN_APP);
        inApp.marcarEnviada();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(inApp));

        var respuesta = notificationService.marcarLeido(1L);

        assertThat(respuesta.estado()).isEqualTo(NotificationStatus.LEIDA);
        assertThat(respuesta.leido()).isTrue();
        assertThat(inApp.getFechaLectura()).isNotNull();
    }

    @Test
    @DisplayName("Un aviso que nunca se entrego no se puede marcar leido")
    void unAvisoNoEntregadoNoSeMarcaLeido() {
        Notification pendiente = aviso(NotificationChannel.IN_APP);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(pendiente));

        // Marcarlo ocultaria que el usuario no llego a verlo.
        assertThatThrownBy(() -> notificationService.marcarLeido(1L))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("no puede pasar a LEIDA");
    }

    @Test
    @DisplayName("Los tipos urgentes son los que exigen actuar hoy")
    void losTiposUrgentesSonLosQueExigenActuar() {
        assertThat(NotificationType.VALOR_CRITICO.esUrgente()).isTrue();
        assertThat(NotificationType.MUESTRA_RECHAZADA.esUrgente()).isTrue();
        assertThat(NotificationType.LOTE_VENCIDO.esUrgente()).isTrue();

        assertThat(NotificationType.CITA_RESERVADA.esUrgente()).isFalse();
        assertThat(NotificationType.RESULTADO_LISTO.esUrgente()).isFalse();
    }

    @Test
    @DisplayName("Los avisos clinicos delicados van al personal, no al paciente")
    void losAvisosDelicadosVanAlPersonal() {
        // Un resultado alarmante por correo, sin nadie que lo interprete ni
        // indique que hacer, es peor que no avisar.
        assertThat(NotificationType.VALOR_CRITICO.esParaPaciente()).isFalse();
        assertThat(NotificationType.MUESTRA_RECHAZADA.esParaPaciente()).isFalse();

        // Y los informativos si van al paciente.
        assertThat(NotificationType.RESULTADO_LISTO.esParaPaciente()).isTrue();
        assertThat(NotificationType.CITA_RESERVADA.esParaPaciente()).isTrue();
    }

    @Test
    @DisplayName("Los canales sin implementacion se declaran como tales")
    void losCanalesSinImplementacionSeDeclaran() {
        assertThat(NotificationChannel.EMAIL.estaImplementado()).isTrue();
        assertThat(NotificationChannel.IN_APP.estaImplementado()).isTrue();
        assertThat(NotificationChannel.PUSH.estaImplementado()).isFalse();
        assertThat(NotificationChannel.SMS.estaImplementado()).isFalse();

        assertThat(NotificationChannel.PUSH.motivoNoDisponible()).contains("Firebase");
        assertThat(NotificationChannel.SMS.motivoNoDisponible()).contains("pasarela");
        assertThat(NotificationChannel.EMAIL.motivoNoDisponible()).isNull();
    }

    @Test
    @DisplayName("Ningun tipo de aviso usa un canal sin implementacion")
    void ningunTipoUsaUnCanalSinImplementacion() {
        // Si un tipo declarara PUSH, sus avisos se crearian y nunca saldrian.
        // Esta comprobacion lo detecta al anadir un tipo nuevo, en lugar de
        // descubrirlo en produccion con un aviso que nadie recibe.
        for (NotificationType tipo : NotificationType.values()) {
            assertThat(tipo.canales())
                    .as("canales de " + tipo)
                    .allMatch(NotificationChannel::estaImplementado);
        }
    }

    @Test
    @DisplayName("Todo tipo de aviso tiene al menos un canal")
    void todoTipoTieneAlMenosUnCanal() {
        for (NotificationType tipo : NotificationType.values()) {
            assertThat(tipo.canales()).as("canales de " + tipo).isNotEmpty();
        }
    }

    // ---- Datos de prueba ---------------------------------------------------

    private NotificationTemplate plantilla(NotificationType tipo, String asunto,
                                           String mensaje, String plantillaHtml,
                                           String enlace) {
        NotificationTemplate plantilla = instanciar(NotificationTemplate.class);
        ReflectionTestUtils.setField(plantilla, "tipo", tipo);
        ReflectionTestUtils.setField(plantilla, "asunto", asunto);
        ReflectionTestUtils.setField(plantilla, "mensaje", mensaje);
        ReflectionTestUtils.setField(plantilla, "plantillaHtml", plantillaHtml);
        ReflectionTestUtils.setField(plantilla, "enlace", enlace);
        ReflectionTestUtils.setField(plantilla, "activa", true);
        return plantilla;
    }

    private Notification aviso(NotificationChannel canal) {
        Notification aviso = new Notification(
                NotificationType.CITA_RESERVADA, canal, "evt-x",
                canal == NotificationChannel.IN_APP ? "paciente:3" : "paciente@correo.local",
                3L, null, "Asunto", "Mensaje", "/ruta", null, Instant.now());
        ReflectionTestUtils.setField(aviso, "id", 1L);
        return aviso;
    }

    /**
     * NotificationTemplate solo existe como fila cargada por la migracion: la
     * aplicacion nunca la construye, asi que su constructor queda protegido
     * para JPA y aqui se instancia por reflexion.
     */
    private <T> T instanciar(Class<T> tipo) {
        try {
            var constructor = tipo.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo instanciar " + tipo.getSimpleName(), e);
        }
    }
}
