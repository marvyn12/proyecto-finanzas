package mx.ipn.finanzas.service;

import mx.ipn.finanzas.model.Clasificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static mx.ipn.finanzas.model.Clasificacion.*;
import static org.junit.jupiter.api.Assertions.*;

/** Módulo 2: el sistema identifica si la cuenta es activo, pasivo o capital y si es de corto o largo plazo. */
class ClasificadorTest {

    private static void espera(String nombre, Clasificacion esperada, boolean resta) {
        Clasificador.Sugerencia s = Clasificador.sugerir(nombre)
                .orElseThrow(() -> new AssertionError("No reconoció «" + nombre + "»"));
        assertEquals(esperada, s.clasificacion(), "Clasificación de «" + nombre + "»");
        assertEquals(resta, s.resta(), "¿«" + nombre + "» resta?");
    }

    @Test
    @DisplayName("Activo circulante: efectivo, cobros a corto plazo, inventarios y pagos anticipados")
    void activoCirculante() {
        espera("Caja", ACTIVO_CIRCULANTE, false);
        espera("Bancos", ACTIVO_CIRCULANTE, false);
        espera("Clientes", ACTIVO_CIRCULANTE, false);
        espera("Documentos por cobrar", ACTIVO_CIRCULANTE, false);
        espera("Deudores diversos", ACTIVO_CIRCULANTE, false);
        espera("Inventario de mercancías", ACTIVO_CIRCULANTE, false);
        espera("Almacén", ACTIVO_CIRCULANTE, false);
        espera("IVA acreditable", ACTIVO_CIRCULANTE, false);
        espera("Anticipo a proveedores", ACTIVO_CIRCULANTE, false);
        espera("Rentas pagadas por anticipado", ACTIVO_CIRCULANTE, false);
        espera("Dividendos por cobrar", ACTIVO_CIRCULANTE, false);
    }

    @Test
    @DisplayName("Activo no circulante: bienes de uso, intangibles y cobros a más de un año")
    void activoNoCirculante() {
        espera("Terrenos", ACTIVO_NO_CIRCULANTE, false);
        espera("Edificios", ACTIVO_NO_CIRCULANTE, false);
        espera("Equipo de transporte", ACTIVO_NO_CIRCULANTE, false);
        espera("Mobiliario y equipo de oficina", ACTIVO_NO_CIRCULANTE, false);
        espera("Patentes y marcas", ACTIVO_NO_CIRCULANTE, false);
        espera("Crédito mercantil", ACTIVO_NO_CIRCULANTE, false);
        espera("Gastos de instalación", ACTIVO_NO_CIRCULANTE, false);
        espera("Documentos por cobrar a largo plazo", ACTIVO_NO_CIRCULANTE, false);
        espera("Inversiones a largo plazo", ACTIVO_NO_CIRCULANTE, false);
    }

    @Test
    @DisplayName("Complementarias: restan dentro de su clasificación")
    void complementarias() {
        espera("Depreciación acumulada de edificios", ACTIVO_NO_CIRCULANTE, true);
        espera("Amortización acumulada", ACTIVO_NO_CIRCULANTE, true);
        espera("Estimación para cuentas incobrables", ACTIVO_CIRCULANTE, true);
        espera("Pérdida del ejercicio", CAPITAL_GANADO, true);
        espera("Pérdidas acumuladas", CAPITAL_GANADO, true);
    }

    @Test
    @DisplayName("Pasivo: corto plazo contra largo plazo")
    void pasivo() {
        espera("Proveedores", PASIVO_CORTO, false);
        espera("Acreedores diversos", PASIVO_CORTO, false);
        espera("Sueldos por pagar", PASIVO_CORTO, false);
        espera("PTU por pagar", PASIVO_CORTO, false);
        espera("Participación de los trabajadores en las utilidades por pagar", PASIVO_CORTO, false);
        espera("IVA trasladado", PASIVO_CORTO, false);
        espera("Anticipo de clientes", PASIVO_CORTO, false);
        espera("Rentas cobradas por anticipado", PASIVO_CORTO, false);
        espera("Préstamos bancarios a corto plazo", PASIVO_CORTO, false);
        espera("Préstamos bancarios a largo plazo", PASIVO_LARGO, false);
        espera("Documentos por pagar a largo plazo", PASIVO_LARGO, false);
        espera("Acreedores hipotecarios", PASIVO_LARGO, false);
        espera("Rentas cobradas por anticipado a largo plazo", PASIVO_LARGO, false);
    }

    @Test
    @DisplayName("Capital contable: contribuido y ganado")
    void capital() {
        espera("Capital social", CAPITAL_CONTRIBUIDO, false);
        espera("Aportaciones para futuros aumentos de capital", CAPITAL_CONTRIBUIDO, false);
        espera("Utilidad del ejercicio", CAPITAL_GANADO, false);
        espera("Utilidades retenidas", CAPITAL_GANADO, false);
        espera("Reserva legal", CAPITAL_GANADO, false);
    }

    @Test
    @DisplayName("No importan mayúsculas, acentos ni espacios de más")
    void normaliza() {
        espera("  DEPRECIACION   ACUMULADA DE EDIFICIOS ", ACTIVO_NO_CIRCULANTE, true);
        espera("proveedores", PASIVO_CORTO, false);
    }

    @Test
    @DisplayName("Si no reconoce el nombre no inventa nada")
    void desconocida() {
        assertTrue(Clasificador.sugerir("Cosa rara").isEmpty());
        assertTrue(Clasificador.sugerir("").isEmpty());
        assertTrue(Clasificador.sugerir(null).isEmpty());
    }

    @Test
    @DisplayName("Todo el catálogo base está bien clasificado según el clasificador")
    void catalogoBaseCoherente() {
        for (CatalogoBase.Cuenta c : CatalogoBase.CUENTAS) {
            espera(c.nombre(), c.clasificacion(), c.resta());
        }
    }
}
