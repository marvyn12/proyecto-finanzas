package mx.ipn.finanzas.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FechasTest {

    @Test
    @DisplayName("Encabezado del balance: «Al 31 de diciembre de 2026»")
    void encabezado() {
        assertEquals("Al 31 de diciembre de 2026", Fechas.al(LocalDate.of(2026, 12, 31)));
        assertEquals("Al 1 de marzo de 2025", Fechas.al(LocalDate.of(2025, 3, 1)));
        assertEquals("31/12/2026", Fechas.corta(LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("Comparativo: años si las dos son cierres, si no la fecha completa")
    void etiquetas() {
        String[] a = Fechas.etiquetas(LocalDate.of(2026, 12, 31), LocalDate.of(2025, 12, 31));
        assertEquals("2026", a[0]);
        assertEquals("2025", a[1]);
        String[] b = Fechas.etiquetas(LocalDate.of(2026, 6, 30), LocalDate.of(2025, 12, 31));
        assertEquals("30/06/2026", b[0]);
        assertEquals("31/12/2025", b[1]);
    }

    @Test
    @DisplayName("Fecha por defecto: la del último balance o el 31 de diciembre del año actual")
    void porDefecto() {
        LocalDate hoy = LocalDate.of(2026, 11, 8);
        assertEquals(LocalDate.of(2026, 12, 31), Fechas.porDefecto(null, hoy));
        assertEquals(LocalDate.of(2025, 6, 30), Fechas.porDefecto(LocalDate.of(2025, 6, 30), hoy));
    }

    @Test
    @DisplayName("Balance dinámico: «Del 1 de enero al 31 de diciembre de 2026»")
    void periodo() {
        assertEquals("Del 1 de enero al 31 de diciembre de 2026", Fechas.periodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertEquals("Del 1 al 31 de marzo de 2026", Fechas.periodo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertEquals("Del 1 de julio de 2025 al 30 de junio de 2026", Fechas.periodo(LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30)));
        assertEquals("Al 31 de diciembre de 2026", Fechas.periodo(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("Comparativo de periodos: años si son años completos, si no el rango")
    void etiquetasPeriodo() {
        String[] a = Fechas.etiquetas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        assertEquals("2026", a[0]);
        assertEquals("2025", a[1]);
        String[] b = Fechas.etiquetas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        assertEquals("01/01/26 – 30/06/26", b[0]);
    }
}
