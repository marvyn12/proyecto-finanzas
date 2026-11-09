package mx.ipn.finanzas.service;

import mx.ipn.finanzas.model.Clasificacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static mx.ipn.finanzas.model.Clasificacion.ACTIVO_CIRCULANTE;
import static mx.ipn.finanzas.model.Clasificacion.ACTIVO_NO_CIRCULANTE;
import static mx.ipn.finanzas.model.Clasificacion.CAPITAL_CONTRIBUIDO;
import static mx.ipn.finanzas.model.Clasificacion.CAPITAL_GANADO;
import static mx.ipn.finanzas.model.Clasificacion.PASIVO_CORTO;
import static mx.ipn.finanzas.model.Clasificacion.PASIVO_LARGO;

/**
 * Catálogo con el que nace cada empresa nueva.
 * Va en el orden clásico: el activo de mayor a menor disponibilidad (liquidez) y el pasivo de mayor a menor
 * exigibilidad (lo que se paga primero va primero). Las complementarias van justo debajo de la cuenta a la que restan.
 */
public final class CatalogoBase {

    private CatalogoBase() {}

    /** Una cuenta del catálogo base. resta = complementaria (su importe se resta). */
    public record Cuenta(Clasificacion clasificacion, String nombre, boolean resta) {}

    public static final List<Cuenta> CUENTAS;

    static {
        List<Cuenta> l = new ArrayList<>();
        // ---------------- Activo circulante (de lo más disponible a lo menos)
        suma(l, ACTIVO_CIRCULANTE, "Caja", "Fondo fijo de caja chica", "Bancos", "Inversiones temporales",
                "Clientes");
        resta(l, ACTIVO_CIRCULANTE, "Estimación para cuentas incobrables");
        suma(l, ACTIVO_CIRCULANTE, "Documentos por cobrar", "Deudores diversos", "Funcionarios y empleados",
                "Anticipo a proveedores", "IVA acreditable", "IVA pendiente de acreditar", "Inventario de mercancías",
                "Mercancías en tránsito", "Papelería y útiles", "Rentas pagadas por anticipado",
                "Seguros pagados por anticipado", "Propaganda y publicidad pagada por anticipado");
        // ---------------- Activo no circulante
        suma(l, ACTIVO_NO_CIRCULANTE, "Terrenos", "Edificios");
        resta(l, ACTIVO_NO_CIRCULANTE, "Depreciación acumulada de edificios");
        suma(l, ACTIVO_NO_CIRCULANTE, "Maquinaria y equipo");
        resta(l, ACTIVO_NO_CIRCULANTE, "Depreciación acumulada de maquinaria y equipo");
        suma(l, ACTIVO_NO_CIRCULANTE, "Mobiliario y equipo de oficina");
        resta(l, ACTIVO_NO_CIRCULANTE, "Depreciación acumulada de mobiliario y equipo de oficina");
        suma(l, ACTIVO_NO_CIRCULANTE, "Equipo de transporte");
        resta(l, ACTIVO_NO_CIRCULANTE, "Depreciación acumulada de equipo de transporte");
        suma(l, ACTIVO_NO_CIRCULANTE, "Equipo de cómputo");
        resta(l, ACTIVO_NO_CIRCULANTE, "Depreciación acumulada de equipo de cómputo");
        suma(l, ACTIVO_NO_CIRCULANTE, "Documentos por cobrar a largo plazo", "Inversiones permanentes en acciones",
                "Depósitos en garantía", "Patentes y marcas", "Crédito mercantil", "Gastos de instalación",
                "Gastos de organización");
        resta(l, ACTIVO_NO_CIRCULANTE, "Amortización acumulada");
        // ---------------- Pasivo a corto plazo (de lo más exigible a lo menos)
        suma(l, PASIVO_CORTO, "Proveedores", "Documentos por pagar", "Acreedores diversos",
                "Préstamos bancarios a corto plazo", "Sueldos por pagar", "Cuotas IMSS e INFONAVIT por pagar",
                "IVA trasladado", "IVA por pagar", "ISR por pagar", "PTU por pagar", "Intereses por pagar",
                "Dividendos por pagar", "Anticipo de clientes", "Rentas cobradas por anticipado",
                "Intereses cobrados por anticipado");
        // ---------------- Pasivo a largo plazo
        suma(l, PASIVO_LARGO, "Préstamos bancarios a largo plazo", "Documentos por pagar a largo plazo",
                "Acreedores hipotecarios", "Obligaciones en circulación", "Rentas cobradas por anticipado a largo plazo");
        // ---------------- Capital contribuido
        suma(l, CAPITAL_CONTRIBUIDO, "Capital social", "Aportaciones para futuros aumentos de capital",
                "Prima en venta de acciones");
        // ---------------- Capital ganado
        suma(l, CAPITAL_GANADO, "Reserva legal", "Utilidades retenidas");
        resta(l, CAPITAL_GANADO, "Pérdidas acumuladas");
        suma(l, CAPITAL_GANADO, "Utilidad del ejercicio");
        resta(l, CAPITAL_GANADO, "Pérdida del ejercicio");
        CUENTAS = Collections.unmodifiableList(l);
    }

    private static void suma(List<Cuenta> l, Clasificacion c, String... nombres) {
        for (String n : nombres) l.add(new Cuenta(c, n, false));
    }

    private static void resta(List<Cuenta> l, Clasificacion c, String nombre) {
        l.add(new Cuenta(c, nombre, true));
    }
}
