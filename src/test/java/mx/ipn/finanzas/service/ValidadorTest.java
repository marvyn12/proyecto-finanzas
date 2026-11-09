package mx.ipn.finanzas.service;

import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.Empresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Validaciones de lo que escribe el usuario (lo que la rúbrica llama "validaciones y manejo de errores"). */
class ValidadorTest {

    private static String error(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(IllegalArgumentException.class, e).getMessage();
    }

    @Test
    @DisplayName("Importes válidos: con o sin miles, con centavos y en cero")
    void importesValidos() {
        assertEquals(new BigDecimal("1500.00"), Validador.importe("1500"));
        assertEquals(new BigDecimal("1500.75"), Validador.importe("1,500.75"));
        assertEquals(new BigDecimal("1500.50"), Validador.importe("$ 1,500.5"));
        assertEquals(new BigDecimal("0.00"), Validador.importe("0"));
        assertEquals(new BigDecimal("9999999999999.99"), Validador.importe("9999999999999.99"));
    }

    @Test
    @DisplayName("Importes inválidos: vacío, letras, negativo, más de 2 decimales, demasiado grande")
    void importesInvalidos() {
        assertTrue(error(() -> Validador.importe("")).contains("Escribe el importe"));
        assertTrue(error(() -> Validador.importe(null)).contains("Escribe el importe"));
        assertTrue(error(() -> Validador.importe("abc")).contains("no es un número"));
        assertTrue(error(() -> Validador.importe("-500")).contains("sin signo menos"));
        assertTrue(error(() -> Validador.importe("10.555")).contains("2 decimales"));
        assertTrue(error(() -> Validador.importe("10000000000000")).contains("demasiado grande"));
    }

    @Test
    @DisplayName("Fechas: ISO válida; vacía, inventada o con año absurdo se rechazan")
    void fechas() {
        assertEquals(LocalDate.of(2026, 12, 31), Validador.fecha("2026-12-31"));
        assertEquals(LocalDate.of(2024, 2, 29), Validador.fecha("2024-02-29"));
        assertTrue(error(() -> Validador.fecha("")).contains("Escribe la fecha"));
        assertTrue(error(() -> Validador.fecha("2026-02-30")).contains("no es válida"));
        assertTrue(error(() -> Validador.fecha("31/12/2026")).contains("no es válida"));
        assertTrue(error(() -> Validador.fecha("1800-01-01")).contains("año"));
        assertTrue(error(() -> Validador.fecha("2500-01-01")).contains("año"));
    }

    @Test
    @DisplayName("Nombres: se limpian espacios; vacíos, larguísimos o solo símbolos se rechazan")
    void textos() {
        assertEquals("Caja chica", Validador.texto("   Caja    chica ", "el nombre", 150));
        assertTrue(error(() -> Validador.texto("   ", "el nombre", 150)).contains("Escribe el nombre"));
        assertTrue(error(() -> Validador.texto("x".repeat(151), "el nombre", 150)).contains("muy largo"));
        assertTrue(error(() -> Validador.texto("---", "el nombre", 150)).contains("letras o números"));
        assertNull(Validador.comentario("   "));
        assertTrue(error(() -> Validador.comentario("x".repeat(256))).contains("muy largo"));
    }

    @Test
    @DisplayName("Clasificación y orden: solo valores de la lista")
    void listas() {
        assertEquals(Clasificacion.PASIVO_CORTO, Validador.clasificacion("PASIVO_CORTO"));
        assertEquals(Clasificacion.ACTIVO_NO_CIRCULANTE, Validador.clasificacion("activo no circulante"));
        assertTrue(error(() -> Validador.clasificacion("Ventas")).contains("clasificación"));
        assertTrue(error(() -> Validador.clasificacion(null)).contains("clasificación"));
        assertEquals(Empresa.ORDEN_CATALOGO, Validador.orden("CATALOGO"));
        assertTrue(error(() -> Validador.orden("AL_REVES")).contains("ordenar"));
    }

    @Test
    @DisplayName("Casillas: 1, true y on cuentan como marcadas")
    void casillas() {
        assertTrue(Validador.casilla("1"));
        assertTrue(Validador.casilla("on"));
        assertTrue(Validador.casilla("TRUE"));
        assertFalse(Validador.casilla("0"));
        assertFalse(Validador.casilla(null));
    }
}
