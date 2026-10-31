package com.medcore.commons.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilTest {

    @Nested
    @DisplayName("Edad")
    class Edad {

        @Test
        @DisplayName("Cuenta anios cumplidos, no anios empezados")
        void cuentaAniosCumplidos() {
            // El dia antes del cumpleanios todavia tiene 33.
            assertThat(DateUtil.edadEnAnios(
                    LocalDate.of(1990, 6, 15), LocalDate.of(2024, 6, 14)))
                    .isEqualTo(33);
            assertThat(DateUtil.edadEnAnios(
                    LocalDate.of(1990, 6, 15), LocalDate.of(2024, 6, 15)))
                    .isEqualTo(34);
        }

        @Test
        @DisplayName("Sin fecha de nacimiento devuelve null, no cero")
        void sinFechaDevuelveNull() {
            // Un cero diria "recien nacido" y le aplicaria el rango de
            // referencia neonatal a un paciente de edad desconocida.
            assertThat(DateUtil.edadEnAnios(null)).isNull();
            assertThat(DateUtil.edadEnAnios(null, LocalDate.now())).isNull();
            assertThat(DateUtil.edadEnMeses(null)).isNull();
        }

        @Test
        @DisplayName("Una fecha de nacimiento futura devuelve null")
        void fechaFuturaDevuelveNull() {
            // Preferible a una edad negativa, que pasaria los filtros de rango
            // como si fuera un dato bueno.
            assertThat(DateUtil.edadEnAnios(
                    LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)))
                    .isNull();
            assertThat(DateUtil.edadEnMeses(LocalDate.now().plusDays(1))).isNull();
        }

        @Test
        @DisplayName("La edad en meses distingue dentro del primer anio")
        void mesesDentroDelPrimerAnio() {
            LocalDate hace7Meses = LocalDate.now(DateUtil.ZONA_LIMA).minusMonths(7);

            // En anios los dos serian 0, y el rango pediatrico no es el mismo
            // para un recien nacido que para un lactante de siete meses.
            assertThat(DateUtil.edadEnAnios(hace7Meses)).isZero();
            assertThat(DateUtil.edadEnMeses(hace7Meses)).isEqualTo(7);
        }

        @Test
        @DisplayName("La edad se calcula a la fecha de la muestra, no a hoy")
        void edadALaFechaDeLaMuestra() {
            LocalDate nacimiento = LocalDate.of(2010, 3, 1);

            // El rango de referencia que vale es el de la edad que tenia el
            // paciente cuando se le tomo la muestra.
            assertThat(DateUtil.edadEnAnios(nacimiento, LocalDate.of(2020, 3, 1)))
                    .isEqualTo(10);
            assertThat(DateUtil.edadEnAnios(nacimiento, LocalDate.of(2026, 3, 1)))
                    .isEqualTo(16);
        }
    }

    @Nested
    @DisplayName("HL7")
    class Hl7 {

        @Test
        @DisplayName("Escribe los catorce digitos sin separadores")
        void escribeCatorceDigitos() {
            String hl7 = DateUtil.aHl7(
                    LocalDateTime.of(2026, 10, 31, 14, 30, 5)
                            .atZone(DateUtil.ZONA_LIMA).toInstant());

            assertThat(hl7).isEqualTo("20261031143005");
        }

        @Test
        @DisplayName("Lee la precision variable que admite el estandar")
        void leePrecisionVariable() {
            // Un equipo puede mandar cualquiera de estas cuatro formas, y
            // rechazar las cortas deja sin leer resultados validos.
            assertThat(DateUtil.deHl7("20261031"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 31, 0, 0));
            assertThat(DateUtil.deHl7("2026103114"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 31, 14, 0));
            assertThat(DateUtil.deHl7("202610311430"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 31, 14, 30));
            assertThat(DateUtil.deHl7("20261031143005"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 31, 14, 30, 5));
        }

        @Test
        @DisplayName("Descarta la zona y los decimales del segundo")
        void descartaZonaYDecimales() {
            assertThat(DateUtil.deHl7("20261031143005+0500"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 31, 14, 30, 5));
            assertThat(DateUtil.deHl7("20261031143005.7"))
                    .isEqualTo(LocalDateTime.of(2026, 10, 31, 14, 30, 5));
        }

        @Test
        @DisplayName("Una fecha ilegible devuelve null y no rompe")
        void fechaIlegibleDevuelveNull() {
            // Un resultado sin fecha vale mas que descartar el resultado
            // entero porque el equipo mando el campo mal.
            assertThat(DateUtil.deHl7("no es una fecha")).isNull();
            assertThat(DateUtil.deHl7("20261345")).isNull();
            assertThat(DateUtil.deHl7("")).isNull();
            assertThat(DateUtil.deHl7(null)).isNull();
        }

        @Test
        @DisplayName("Un instante nulo da cadena vacia, no la palabra null")
        void instanteNuloDaCadenaVacia() {
            assertThat(DateUtil.aHl7(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Para leer")
    class ParaLeer {

        @Test
        @DisplayName("Escribe la hora de Lima, no la UTC")
        void escribeHoraDeLima() {
            // 19:30 UTC son las 14:30 en Lima. Un correo que diga la hora UTC
            // manda al paciente cinco horas tarde.
            Instant instante = Instant.parse("2026-10-31T19:30:00Z");

            assertThat(DateUtil.legible(instante)).isEqualTo("31/10/2026 14:30");
            assertThat(DateUtil.hora(instante)).isEqualTo("14:30");
            assertThat(DateUtil.diaDe(instante)).isEqualTo(LocalDate.of(2026, 10, 31));
        }

        @Test
        @DisplayName("El dia de Lima no es el de UTC en la franja de la noche")
        void elDiaCambiaEnLaFranjaDeLaNoche() {
            // Las 02:00 UTC del dia 1 son las 21:00 del dia anterior en Lima.
            Instant instante = Instant.parse("2026-11-01T02:00:00Z");

            assertThat(DateUtil.diaDe(instante)).isEqualTo(LocalDate.of(2026, 10, 31));
        }

        @Test
        @DisplayName("El mes en palabras va en castellano")
        void mesEnPalabras() {
            // Escrito a mano: el JDK del contenedor puede no traer los datos de
            // la configuracion regional y saldria en ingles.
            assertThat(DateUtil.enPalabras(LocalDate.of(2026, 10, 31)))
                    .isEqualTo("31 de octubre de 2026");
            assertThat(DateUtil.enPalabras(LocalDate.of(2026, 1, 1)))
                    .isEqualTo("1 de enero de 2026");
            assertThat(DateUtil.enPalabras(LocalDate.of(2026, 12, 25)))
                    .isEqualTo("25 de diciembre de 2026");
        }

        @Test
        @DisplayName("Lo nulo sale como cadena vacia")
        void loNuloSaleVacio() {
            assertThat(DateUtil.legible((LocalDate) null)).isEmpty();
            assertThat(DateUtil.legible((Instant) null)).isEmpty();
            assertThat(DateUtil.hora(null)).isEmpty();
            assertThat(DateUtil.enPalabras(null)).isEmpty();
            assertThat(DateUtil.diaDe(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Rangos del dia")
    class Rangos {

        @Test
        @DisplayName("El dia va de medianoche de Lima a medianoche de Lima")
        void elDiaVaDeMedianocheAMedianoche() {
            LocalDate dia = LocalDate.of(2026, 10, 31);

            // Medianoche en Lima son las 05:00 UTC.
            assertThat(DateUtil.inicioDelDia(dia))
                    .isEqualTo(Instant.parse("2026-10-31T05:00:00Z"));
            assertThat(DateUtil.finDelDia(dia))
                    .isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        }

        @Test
        @DisplayName("El fin del dia es abierto, para no perder el ultimo instante")
        void elFinDelDiaEsAbierto() {
            LocalDate dia = LocalDate.of(2026, 10, 31);
            Instant casiMedianoche = Instant.parse("2026-11-01T04:59:59.700Z");

            // Un <= 23:59:59 habria dejado fuera este registro; el limite
            // abierto lo incluye.
            assertThat(casiMedianoche).isBefore(DateUtil.finDelDia(dia));
            assertThat(DateUtil.diaDe(casiMedianoche)).isEqualTo(dia);
        }
    }

    @Test
    @DisplayName("La clase de utilidades no se instancia")
    void noSeInstancia() throws Exception {
        var constructor = DateUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .hasRootCauseInstanceOf(AssertionError.class);
    }
}
