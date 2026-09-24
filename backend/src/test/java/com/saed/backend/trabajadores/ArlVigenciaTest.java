package com.saed.backend.trabajadores;

import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de Pruebas Unitarias para la Lógica de Vigencia de ARL (GAP-F9-05 / Observación A).
 *
 * Valida de forma determinística e inequívoca todos los casos frontera y combinaciones de fechas:
 * 1. Afiliación futura (afiliacion > hoy) -> NO VIGENTE.
 * 2. Afiliación hoy (afiliacion = hoy) -> VIGENTE.
 * 3. Afiliación pasada y vencimiento futuro (afiliacion < hoy < vencimiento) -> VIGENTE.
 * 4. Vencimiento hoy (vencimiento = hoy) -> VIGENTE (vigente durante todo el día de expiración).
 * 5. Vencimiento pasado (vencimiento < hoy) -> NO VIGENTE.
 * 6. Sin vencimiento (vencimiento == null) -> NO VIGENTE.
 * 7. Rango incoherente (afiliacion > vencimiento) -> NO VIGENTE.
 * 8. Sin afiliación pero con vencimiento futuro (legacy) -> VIGENTE.
 * 9. Sin afiliación pero con vencimiento pasado (legacy) -> NO VIGENTE.
 * 10. Ambas fechas nulas -> NO VIGENTE.
 */
class ArlVigenciaTest {

    private final LocalDate hoy = LocalDate.of(2026, 9, 18);

    @Test
    @DisplayName("Caso 1: Afiliación futura (fecha_afiliacion > hoy) -> NO VIGENTE")
    void caso1_afiliacionFutura_retornaFalse() {
        LocalDate afiliacion = hoy.plusDays(1);
        LocalDate vencimiento = hoy.plusDays(30);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertFalse(vigente, "Un trabajador con afiliación futura no debe considerarse con ARL vigente");
    }

    @Test
    @DisplayName("Caso 2: Afiliación hoy (fecha_afiliacion = hoy) -> VIGENTE")
    void caso2_afiliacionHoy_retornaTrue() {
        LocalDate afiliacion = hoy;
        LocalDate vencimiento = hoy.plusDays(30);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertTrue(vigente, "Un trabajador cuya afiliación inicia hoy debe considerarse con ARL vigente");
    }

    @Test
    @DisplayName("Caso 3: Afiliación pasada y vencimiento futuro -> VIGENTE")
    void caso3_afiliacionPasadaYVencimientoFuturo_retornaTrue() {
        LocalDate afiliacion = hoy.minusDays(15);
        LocalDate vencimiento = hoy.plusDays(15);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertTrue(vigente, "Un trabajador con afiliación previa y vencimiento futuro debe estar vigente");
    }

    @Test
    @DisplayName("Caso 4: Vencimiento hoy (fecha_vencimiento = hoy) -> VIGENTE (frontera inclusiva)")
    void caso4_vencimientoHoy_retornaTrue() {
        LocalDate afiliacion = hoy.minusDays(30);
        LocalDate vencimiento = hoy;

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertTrue(vigente, "En la fecha exacta de vencimiento la ARL aún ampara al trabajador");
    }

    @Test
    @DisplayName("Caso 5: Vencimiento pasado (fecha_vencimiento < hoy) -> NO VIGENTE")
    void caso5_vencimientoPasado_retornaFalse() {
        LocalDate afiliacion = hoy.minusDays(60);
        LocalDate vencimiento = hoy.minusDays(1);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertFalse(vigente, "Un trabajador con fecha de vencimiento anterior a hoy debe ser rechazado como no vigente");
    }

    @Test
    @DisplayName("Caso 6: Sin vencimiento (vencimiento == null) -> NO VIGENTE")
    void caso6_sinVencimiento_retornaFalse() {
        LocalDate afiliacion = hoy.minusDays(10);
        LocalDate vencimiento = null;

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertFalse(vigente, "Una afiliación sin fecha de vencimiento no tiene vigencia válida comprobable");
    }

    @Test
    @DisplayName("Caso 7: Rango incoherente (afiliacion > vencimiento) -> NO VIGENTE")
    void caso7_rangoInvalido_afiliacionPosteriorAVencimiento_retornaFalse() {
        LocalDate afiliacion = hoy.plusDays(10);
        LocalDate vencimiento = hoy.plusDays(5);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertFalse(vigente, "Fechas inconsistentes donde afiliación es posterior a vencimiento deben ser rechazadas");
    }

    @Test
    @DisplayName("Caso 8: Sin afiliación registrada pero con vencimiento futuro (compatibilidad legacy) -> VIGENTE")
    void caso8_sinAfiliacionConVencimientoFuturo_retornaTrue() {
        LocalDate afiliacion = null;
        LocalDate vencimiento = hoy.plusDays(20);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertTrue(vigente, "Registros legacy sin fecha de afiliación pero con vencimiento futuro deben ser vigentes");
    }

    @Test
    @DisplayName("Caso 9: Sin afiliación y vencimiento pasado -> NO VIGENTE")
    void caso9_sinAfiliacionConVencimientoPasado_retornaFalse() {
        LocalDate afiliacion = null;
        LocalDate vencimiento = hoy.minusDays(10);

        boolean vigente = TrabajadorDTO.calcularArlVigente(vencimiento, afiliacion, hoy);

        assertFalse(vigente, "Registros legacy con vencimiento en el pasado no son vigentes");
    }

    @Test
    @DisplayName("Caso 10: Ambas fechas nulas -> NO VIGENTE")
    void caso10_ambasFechasNulas_retornaFalse() {
        boolean vigente = TrabajadorDTO.calcularArlVigente(null, null, hoy);

        assertFalse(vigente, "Sin fechas de ARL el resultado debe ser siempre false");
    }
}
