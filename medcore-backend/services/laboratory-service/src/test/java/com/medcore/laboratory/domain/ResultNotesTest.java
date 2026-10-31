package com.medcore.laboratory.domain;

import com.medcore.laboratory.domain.entity.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las notas de un resultado.
 *
 * <p>Importan porque es donde queda escrita la discrepancia de unidad del
 * equipo, y esa advertencia no puede pisar lo que escribio el laboratorista.
 */
class ResultNotesTest {

    @Test
    @DisplayName("Una nota adicional no borra la que ya habia")
    void laNotaAdicionalNoBorra() {
        Result resultado = resultado();
        resultado.anotar("Muestra ligeramente hemolizada");

        resultado.anotarAdicional("El equipo informo la unidad /uL y el "
                + "catalogo usa x10^3/uL.");

        // La nota del laboratorista vale: una advertencia tecnica no tiene por
        // que pisar lo que dijo una persona.
        assertThat(resultado.getObservaciones())
                .contains("hemolizada")
                .contains("x10^3/uL");
    }

    @Test
    @DisplayName("Sin nota previa, la adicional queda sola")
    void sinNotaPreviaQuedaSola() {
        Result resultado = resultado();

        resultado.anotarAdicional("Unidad discrepante");

        assertThat(resultado.getObservaciones()).isEqualTo("Unidad discrepante");
    }

    @Test
    @DisplayName("La misma nota no se repite")
    void laMismaNotaNoSeRepite() {
        Result resultado = resultado();

        resultado.anotarAdicional("Unidad discrepante");
        resultado.anotarAdicional("Unidad discrepante");

        // Un reenvio del equipo no debe dejar la nota escrita dos veces.
        assertThat(resultado.getObservaciones()).isEqualTo("Unidad discrepante");
    }

    @Test
    @DisplayName("Una nota vacia no cambia nada")
    void notaVaciaNoCambiaNada() {
        Result resultado = resultado();
        resultado.anotar("Original");

        resultado.anotarAdicional(null);
        resultado.anotarAdicional("   ");

        assertThat(resultado.getObservaciones()).isEqualTo("Original");
    }

    @Test
    @DisplayName("Si no cabe, se conserva lo que ya estaba")
    void siNoCabeSeConservaLoAnterior() {
        Result resultado = resultado();
        String larga = "x".repeat(480);
        resultado.anotar(larga);

        resultado.anotarAdicional("Una advertencia que no cabe en lo que queda");

        // La nota del laboratorista vale mas que la advertencia, y la
        // advertencia tambien va al log.
        assertThat(resultado.getObservaciones()).isEqualTo(larga);
        assertThat(resultado.getObservaciones().length()).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("Una nota adicional mas larga que la columna se recorta")
    void notaAdicionalLargaSeRecorta() {
        Result resultado = resultado();

        resultado.anotarAdicional("y".repeat(600));

        // Recortar es mejor que fallar al guardar: el resultado tiene que
        // quedar registrado.
        assertThat(resultado.getObservaciones().length()).isEqualTo(500);
    }

    private Result resultado() {
        // La muestra no interviene en las notas; el constructor solo guarda
        // la referencia.
        return new Result(null, "HEM-003", "Leucocitos",
                8.2, null, "x10^3/uL", "EQUIPO", "ANALIZADOR");
    }
}
