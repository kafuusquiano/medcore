package com.medcore.patient.service;

import com.medcore.patient.domain.dto.request.PatientRequest;
import com.medcore.patient.domain.dto.request.PatientSearchRequest;
import com.medcore.patient.domain.dto.response.PatientResponse;
import com.medcore.patient.domain.entity.Patient;
import com.medcore.patient.domain.enums.BloodType;
import com.medcore.patient.domain.enums.Gender;
import com.medcore.patient.domain.enums.PatientStatus;
import com.medcore.patient.event.producer.PatientEventProducer;
import com.medcore.patient.exception.PatientException;
import com.medcore.patient.mapper.PatientMapper;
import com.medcore.patient.repository.PatientRepository;
import com.medcore.patient.service.impl.PatientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientServiceImplTest {

    private static final String REQUEST_ID = "req-test-1";

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private PatientEventProducer eventProducer;

    private PatientServiceImpl patientService;

    private Patient paciente;

    @BeforeEach
    void preparar() {
        patientService = new PatientServiceImpl(patientRepository, patientMapper, eventProducer);

        paciente = new Patient("12345678", "Ana", "Torres",
                LocalDate.of(1990, 5, 20), Gender.FEMENINO, BloodType.O_POS,
                "999888777", "ana@correo.com", "Av. Siempre Viva 123", "Callao");
        ReflectionTestUtils.setField(paciente, "id", 1L);

        when(patientRepository.save(any())).thenReturn(paciente);
        when(patientMapper.aRespuesta(any())).thenReturn(respuestaDe(paciente));
    }

    private PatientRequest requestValido() {
        return new PatientRequest("12345678", "Ana", "Torres",
                LocalDate.of(1990, 5, 20), Gender.FEMENINO, BloodType.O_POS,
                "999888777", "ANA@Correo.com", "Av. Siempre Viva 123", "Callao",
                "Luis Torres", "988777666", "Penicilina");
    }

    @Test
    @DisplayName("crear_dniNuevo_guardaYPublicaEvento")
    void crear_dniNuevo_guardaYPublicaEvento() {
        when(patientRepository.existsByDni("12345678")).thenReturn(false);

        PatientResponse respuesta = patientService.crear(requestValido(), REQUEST_ID);

        assertThat(respuesta.dni()).isEqualTo("12345678");
        verify(patientRepository).save(any(Patient.class));
        // Sin transaccion activa en el test, la publicacion ocurre en linea.
        verify(eventProducer).publicarCreado(any(Patient.class), eq(REQUEST_ID));
    }

    @Test
    @DisplayName("crear_dniRepetido_lanzaConflictoYNoPublica")
    void crear_dniRepetido_lanzaConflictoYNoPublica() {
        when(patientRepository.existsByDni("12345678")).thenReturn(true);

        assertThatThrownBy(() -> patientService.crear(requestValido(), REQUEST_ID))
                .isInstanceOf(PatientException.class)
                .hasFieldOrPropertyWithValue("codigo", "DNI_YA_REGISTRADO");

        verify(patientRepository, never()).save(any());
        verify(eventProducer, never()).publicarCreado(any(), anyString());
    }

    @Test
    @DisplayName("crear_correoConMayusculas_seNormalizaAMinusculas")
    void crear_correoConMayusculas_seNormalizaAMinusculas() {
        when(patientRepository.existsByDni(anyString())).thenReturn(false);

        patientService.crear(requestValido(), REQUEST_ID);

        var captor = org.mockito.ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        assertThat(captor.getValue().getCorreo()).isEqualTo("ana@correo.com");
    }

    @Test
    @DisplayName("actualizar_noCambiaElDni")
    void actualizar_noCambiaElDni() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(paciente));

        // El request trae otro DNI: debe ignorarse, porque cambiarlo dejaria
        // el historial clinico atribuido a otra persona.
        PatientRequest conOtroDni = new PatientRequest("87654321", "Ana Maria", "Torres",
                LocalDate.of(1990, 5, 20), Gender.FEMENINO, BloodType.O_POS,
                "999888777", "ana@correo.com", "Nueva direccion", "Callao",
                null, null, null);

        patientService.actualizar(1L, conOtroDni, REQUEST_ID);

        assertThat(paciente.getDni()).isEqualTo("12345678");
        assertThat(paciente.getNombres()).isEqualTo("Ana Maria");
    }

    @Test
    @DisplayName("obtenerPorId_inexistente_lanzaNoEncontrado")
    void obtenerPorId_inexistente_lanzaNoEncontrado() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.obtenerPorId(99L))
                .isInstanceOf(PatientException.class)
                .hasFieldOrPropertyWithValue("codigo", "PACIENTE_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("buscar_edadMinMayorQueMax_lanzaRangoInvalido")
    void buscar_edadMinMayorQueMax_lanzaRangoInvalido() {
        var criterios = new PatientSearchRequest(null, null, null, null, 60, 20);

        assertThatThrownBy(() -> patientService.buscar(criterios, org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(PatientException.class)
                .hasFieldOrPropertyWithValue("codigo", "RANGO_EDAD_INVALIDO");
    }

    @Test
    @DisplayName("reactivar_pacienteFallecido_lanzaIllegalState")
    void reactivar_pacienteFallecido_lanzaIllegalState() {
        paciente.marcarFallecido();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(paciente));

        // Una ficha cerrada por fallecimiento no se reabre.
        assertThatThrownBy(() -> patientService.reactivar(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallecido");
    }

    @Test
    @DisplayName("desactivar_dejaLaFichaSinAdmitirAtenciones")
    void desactivar_dejaLaFichaSinAdmitirAtenciones() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(paciente));

        patientService.desactivar(1L);

        assertThat(paciente.getEstado()).isEqualTo(PatientStatus.INACTIVO);
        assertThat(paciente.admiteAtencion()).isFalse();
    }

    @Test
    @DisplayName("edad_seCalculaYNoSeAlmacena")
    void edad_seCalculaYNoSeAlmacena() {
        Patient nacidoHace30 = new Patient("11111111", "Luis", "Perez",
                LocalDate.now().minusYears(30).minusDays(1), Gender.MASCULINO,
                BloodType.DESCONOCIDO, null, null, null, null);

        assertThat(nacidoHace30.edad()).isEqualTo(30);
        assertThat(nacidoHace30.esMenorDeEdad()).isFalse();
    }

    @Test
    @DisplayName("esMenorDeEdad_nacidoHace17Anos_devuelveTrue")
    void esMenorDeEdad_nacidoHace17Anos_devuelveTrue() {
        Patient menor = new Patient("22222222", "Sofia", "Ramos",
                LocalDate.now().minusYears(17), Gender.FEMENINO,
                BloodType.DESCONOCIDO, null, null, null, null);

        assertThat(menor.esMenorDeEdad()).isTrue();
    }

    private static PatientResponse respuestaDe(Patient p) {
        return new PatientResponse(p.getId(), p.getDni(), p.getNombres(), p.getApellidos(),
                p.nombreCompleto(), p.getFechaNacimiento(), p.edad(), p.esMenorDeEdad(),
                p.getSexo(), p.getTipoSangre(), p.getTipoSangre().etiqueta(),
                p.getTelefono(), p.getCorreo(), p.getDireccion(), p.getZona(),
                null, null, null, p.getEstado(), null, null);
    }
}
