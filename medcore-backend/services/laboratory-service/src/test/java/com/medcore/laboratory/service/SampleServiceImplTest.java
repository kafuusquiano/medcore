package com.medcore.laboratory.service;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.laboratory.client.PatientClient;
import com.medcore.laboratory.client.PatientDto;
import com.medcore.laboratory.domain.dto.request.SampleRequest;
import com.medcore.laboratory.domain.entity.PendingLabOrder;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.enums.SampleStatus;
import com.medcore.laboratory.event.producer.SampleEventProducer;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.mapper.ResultMapper;
import com.medcore.laboratory.mapper.SampleMapper;
import com.medcore.laboratory.repository.PendingLabOrderRepository;
import com.medcore.laboratory.repository.ResultRepository;
import com.medcore.laboratory.repository.SampleRepository;
import com.medcore.laboratory.repository.TestCatalogRepository;
import com.medcore.laboratory.service.impl.SampleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del ciclo de vida del tubo.
 *
 * <p>Fuera de una transaccion real, {@code publicarTrasCommit} publica de
 * inmediato porque no hay sincronizacion activa, lo que permite verificar
 * aqui que se publica lo correcto.
 */
@ExtendWith(MockitoExtension.class)
class SampleServiceImplTest {

    private static final Long ID_ORDEN = 10L;
    private static final Long ID_MUESTRA = 7L;

    @Mock
    private SampleRepository sampleRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private PendingLabOrderRepository ordenRepository;

    @Mock
    private TestCatalogRepository catalogoRepository;

    @Mock
    private PatientClient patientClient;

    @Mock
    private SampleEventProducer eventProducer;

    private SampleServiceImpl sampleService;

    @BeforeEach
    void prepararServicio() {
        sampleService = new SampleServiceImpl(
                sampleRepository, resultRepository, ordenRepository, catalogoRepository,
                patientClient, eventProducer, new SampleMapper(new ResultMapper()));

        lenient().when(sampleRepository.save(any(Sample.class))).thenAnswer(invocacion -> {
            Sample guardada = invocacion.getArgument(0);
            if (guardada.getId() == null) {
                ReflectionTestUtils.setField(guardada, "id", ID_MUESTRA);
            }
            if (guardada.getFechaToma() == null) {
                ReflectionTestUtils.setField(guardada, "fechaToma", Instant.now());
            }
            return guardada;
        });
    }

    @Test
    @DisplayName("Registra la muestra con el sexo y la edad que da patient-service")
    void registraLaMuestraConSexoYEdad() {
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("RUTINA")));
        when(sampleRepository.muestraVivaDeOrden(ID_ORDEN)).thenReturn(Optional.empty());
        when(sampleRepository.contarEntre(any(), any())).thenReturn(11L);
        when(patientClient.obtenerPorId(eq(3L), anyString(), anyString(), any()))
                .thenReturn(ApiResponse.ok("ok", new PatientDto(
                        3L, "44556677", "PEREZ QUISPE, Maria Elena", "maria@correo.local",
                        34, "FEMENINO", "San Juan de Lurigancho")));

        var respuesta = sampleService.registrar(
                new SampleRequest(ID_ORDEN, "SANGRE", false, "Tec. Lopez", "Tubo con EDTA"),
                "tecnico1", "req-1");

        // El correlativo sigue a las muestras ya registradas hoy.
        assertThat(respuesta.codigo()).endsWith("-0012");
        assertThat(respuesta.sexoPaciente()).isEqualTo("FEMENINO");
        assertThat(respuesta.edadPaciente()).isEqualTo(34);
        assertThat(respuesta.estado()).isEqualTo(SampleStatus.REGISTRADA);
        // Recien registrada todavia no admite resultados: falta montarla en
        // el equipo.
        assertThat(respuesta.admiteResultados()).isFalse();

        verify(eventProducer).publicarRegistrada(any(), eq("req-1"));
    }

    @Test
    @DisplayName("Registra la muestra sin sexo ni edad si patient-service no responde")
    void registraLaMuestraAunqueNoRespondaPatientService() {
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("RUTINA")));
        when(sampleRepository.muestraVivaDeOrden(ID_ORDEN)).thenReturn(Optional.empty());
        when(patientClient.obtenerPorId(anyLong(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("503 Service Unavailable"));

        var respuesta = sampleService.registrar(
                new SampleRequest(ID_ORDEN, "SANGRE", false, "Tec. Lopez", null),
                "tecnico1", "req-2");

        // Que el padron de pacientes este caido no debe impedir recibir un
        // tubo de sangre: la muestra se registra y los datos del paciente son
        // los que trajo el evento.
        assertThat(respuesta.nombrePaciente()).isEqualTo("PEREZ QUISPE, Maria Elena");
        assertThat(respuesta.sexoPaciente()).isNull();
        assertThat(respuesta.edadPaciente()).isNull();
    }

    @Test
    @DisplayName("La urgencia de la orden manda sobre la casilla de recepcion")
    void laUrgenciaDeLaOrdenManda() {
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("URGENTE")));
        when(sampleRepository.muestraVivaDeOrden(ID_ORDEN)).thenReturn(Optional.empty());
        when(patientClient.obtenerPorId(anyLong(), anyString(), anyString(), any()))
                .thenReturn(ApiResponse.ok("ok", null));

        // Si el medico la marco urgente, el tubo encabeza la cola aunque en
        // recepcion no se haya marcado la casilla.
        var respuesta = sampleService.registrar(
                new SampleRequest(ID_ORDEN, "SANGRE", false, null, null),
                "tecnico1", "req-3");

        assertThat(respuesta.urgente()).isTrue();
    }

    @Test
    @DisplayName("Un indicador de urgencia omitido vale false")
    void unIndicadorDeUrgenciaOmitidoValeFalse() {
        // Jackson 3, el de Spring Boot 4, rechaza con 400 un cuerpo que omite
        // un primitivo, asi que el campo se declara como Boolean. Se detecto
        // contra el servicio real: un POST sin "urgente" devolvia
        // JSON_INVALIDO, y un formulario que no envia la casilla sin marcar
        // es lo normal.
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("RUTINA")));
        when(sampleRepository.muestraVivaDeOrden(ID_ORDEN)).thenReturn(Optional.empty());
        when(patientClient.obtenerPorId(anyLong(), anyString(), anyString(), any()))
                .thenReturn(ApiResponse.ok("ok", null));

        var respuesta = sampleService.registrar(
                new SampleRequest(ID_ORDEN, "SANGRE", null, null, null), "tecnico1", "req-11");

        assertThat(respuesta.urgente()).isFalse();
    }

    @Test
    @DisplayName("Rechaza registrar una segunda muestra viva para la misma orden")
    void rechazaUnaSegundaMuestraVivaDeLaMismaOrden() {
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("RUTINA")));
        when(sampleRepository.muestraVivaDeOrden(ID_ORDEN))
                .thenReturn(Optional.of(muestraRegistrada()));

        assertThatThrownBy(() -> sampleService.registrar(
                new SampleRequest(ID_ORDEN, "SANGRE", false, null, null), "tecnico1", "req-4"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("ya tiene una muestra registrada");

        verify(sampleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza registrar si la orden no esta en la cola")
    void rechazaRegistrarSiLaOrdenNoEstaEnLaCola() {
        when(ordenRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sampleService.registrar(
                new SampleRequest(999L, "SANGRE", false, null, null), "tecnico1", "req-5"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("No hay ninguna orden pendiente");
    }

    @Test
    @DisplayName("El rechazo exige motivo")
    void elRechazoExigeMotivo() {
        // El medico necesita saber por que se rechazo para repetir la toma.
        assertThatThrownBy(() -> sampleService.rechazar(ID_MUESTRA, "  ", "req-6"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("Indique por que se rechaza");

        verify(sampleRepository, never()).save(any());
    }

    @Test
    @DisplayName("El rechazo devuelve la orden a la cola sin tocar su contenido")
    void elRechazoDevuelveLaOrdenALaCola() {
        Sample muestra = muestraRegistrada();
        PendingLabOrder orden = orden("RUTINA");
        orden.marcarAtendida();

        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden));

        var respuesta = sampleService.rechazar(ID_MUESTRA, "Muestra hemolizada", "req-7");

        assertThat(respuesta.estado()).isEqualTo(SampleStatus.RECHAZADA);
        assertThat(respuesta.motivoRechazo()).isEqualTo("Muestra hemolizada");

        // La orden vuelve a estar pendiente, y sus pruebas siguen intactas.
        // Se comprueba sobre la entidad y no solo con un verify del save
        // porque el primer intento recreaba la orden con su coleccion de
        // pruebas, lo que duplicaba las filas de pending_lab_order_tests y
        // fallaba con DATOS_DUPLICADOS contra la base real.
        assertThat(orden.isAtendida()).isFalse();
        assertThat(orden.getPruebas()).containsExactly("HEM-001", "HEM-004");
        verify(ordenRepository).save(orden);

        verify(eventProducer).publicarRechazada(any(), eq("Muestra hemolizada"), eq("req-7"));
    }

    @Test
    @DisplayName("No cierra la muestra si falta informar una prueba del catalogo")
    void noCierraSiFaltaUnaPruebaDelCatalogo() {
        Sample muestra = muestraEnProceso();
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("RUTINA")));
        when(resultRepository.findByMuestraIdOrderByCodigoPrueba(ID_MUESTRA))
                .thenReturn(List.of(resultado(muestra, "HEM-001")));
        // HEM-004 esta en el catalogo y no se ha informado.
        when(catalogoRepository.existsById("HEM-004")).thenReturn(true);

        assertThatThrownBy(() -> sampleService.marcarProcesada(ID_MUESTRA, "req-8"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Faltan resultados de HEM-004");
    }

    @Test
    @DisplayName("Cierra la muestra ignorando una prueba que no esta en el catalogo")
    void cierraIgnorandoUnaPruebaFueraDeCatalogo() {
        // Caso detectado con una orden real: pedia un codigo que el catalogo
        // del laboratorio no tenia. El laboratorio no puede informar lo que no
        // sabe hacer, asi que exigirlo dejaria el tubo abierto para siempre.
        Sample muestra = muestraEnProceso();
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(muestra));
        when(ordenRepository.findById(ID_ORDEN)).thenReturn(Optional.of(orden("RUTINA")));
        when(resultRepository.findByMuestraIdOrderByCodigoPrueba(ID_MUESTRA))
                .thenReturn(List.of(resultado(muestra, "HEM-001")));
        when(catalogoRepository.existsById("HEM-004")).thenReturn(false);

        var respuesta = sampleService.marcarProcesada(ID_MUESTRA, "req-9");

        assertThat(respuesta.estado()).isEqualTo(SampleStatus.PROCESADA);
    }

    @Test
    @DisplayName("No permite una transicion que la maquina de estados prohibe")
    void noPermiteUnaTransicionProhibida() {
        Sample procesada = muestraEnProceso();
        procesada.marcarProcesada();
        when(sampleRepository.findById(ID_MUESTRA)).thenReturn(Optional.of(procesada));

        // Una muestra cerrada no vuelve a proceso: lo que se corrige es el
        // resultado, no el estado del tubo.
        assertThatThrownBy(() -> sampleService.iniciarProceso(ID_MUESTRA, "req-10"))
                .isInstanceOf(LaboratoryException.class)
                .hasMessageContaining("no puede pasar a EN_PROCESO");
    }

    // ---- Datos de prueba ---------------------------------------------------

    private PendingLabOrder orden(String prioridad) {
        return new PendingLabOrder(
                ID_ORDEN, "ORD-20261029-0001", 900L, 3L, "44556677",
                "PEREZ QUISPE, Maria Elena", "maria@correo.local", 9L, "Dr. Ramirez",
                prioridad, "Control post transfusion", List.of("HEM-001", "HEM-004"),
                Instant.now());
    }

    private Sample muestraRegistrada() {
        Sample sample = new Sample(
                "MUE-20261029-0001", ID_ORDEN, "ORD-20261029-0001", 3L,
                "44556677", "PEREZ QUISPE, Maria Elena", "maria@correo.local",
                "FEMENINO", 34, "San Juan de Lurigancho",
                9L, "SANGRE", false, "tecnico1");
        ReflectionTestUtils.setField(sample, "id", ID_MUESTRA);
        ReflectionTestUtils.setField(sample, "fechaToma", Instant.now());
        return sample;
    }

    private Sample muestraEnProceso() {
        Sample sample = muestraRegistrada();
        sample.iniciarProceso();
        return sample;
    }

    private Result resultado(Sample muestra, String codigoPrueba) {
        Result resultado = new Result(muestra, codigoPrueba, "Prueba",
                13.0, null, "g/dL", "MANUAL", "tecnico1");
        ReflectionTestUtils.setField(resultado, "id", 100L);
        ReflectionTestUtils.setField(resultado, "fechaResultado", Instant.now());
        return resultado;
    }
}
