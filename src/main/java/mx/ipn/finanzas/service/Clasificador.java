package mx.ipn.finanzas.service;

import mx.ipn.finanzas.model.Clasificacion;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static mx.ipn.finanzas.model.Clasificacion.ACTIVO_CIRCULANTE;
import static mx.ipn.finanzas.model.Clasificacion.ACTIVO_NO_CIRCULANTE;
import static mx.ipn.finanzas.model.Clasificacion.CAPITAL_CONTRIBUIDO;
import static mx.ipn.finanzas.model.Clasificacion.CAPITAL_GANADO;
import static mx.ipn.finanzas.model.Clasificacion.PASIVO_CORTO;
import static mx.ipn.finanzas.model.Clasificacion.PASIVO_LARGO;

/**
 * Módulo 2 (clasificación financiera): sugiere la clasificación de una cuenta a partir de su nombre.
 * Se usa para proponer la clasificación al dar de alta una cuenta nueva y para avisar si una cuenta
 * parece estar mal clasificada (p. ej. "Proveedores" capturada como activo).
 *
 * Son reglas por palabras clave, revisadas en orden: la primera que coincide gana.
 * Por eso las más específicas van primero (p. ej. "anticipo de clientes" antes que "clientes").
 */
public final class Clasificador {

    private Clasificador() {}

    /** Lo que propone el clasificador y por qué. */
    public record Sugerencia(Clasificacion clasificacion, boolean resta, String motivo) {}

    private record Regla(Pattern patron, Clasificacion clasificacion, boolean resta, String motivo) {}

    private static Regla regla(String regex, Clasificacion c, boolean resta, String motivo) {
        return new Regla(Pattern.compile(regex), c, resta, motivo);
    }

    private static final List<Regla> REGLAS = List.of(
            // ---- Complementarias (restan)
            regla("\\b(depreciacion|amortizacion)(es)? acumulada", ACTIVO_NO_CIRCULANTE, true,
                    "Es complementaria de un activo no circulante: disminuye su valor, por eso resta."),
            regla("estimacion (para|de) (cuentas )?(incobrables|cobro dudoso)|cuentas incobrables|cobro dudoso",
                    ACTIVO_CIRCULANTE, true,
                    "Es complementaria de Clientes: lo que probablemente no se va a cobrar; resta."),
            regla("\\bperdidas? (del ejercicio|neta|acumuladas?|de ejercicios anteriores)", CAPITAL_GANADO, true,
                    "Una pérdida disminuye el capital ganado, por eso resta."),

            // ---- Derechos de cobro (activo)
            regla("por cobrar.*largo plazo|largo plazo.*por cobrar", ACTIVO_NO_CIRCULANTE, false,
                    "Es dinero que le deben a la empresa, pero se cobra en más de un año."),
            regla("anticipos? (de|a) clientes|cobrad[oa]s? por anticipado|ingresos? (cobrados )?por anticipado|ingresos diferidos",
                    null, false, "Se cobró antes de entregar: la empresa le debe el bien o servicio al cliente."),
            regla("por cobrar|\\bdeudores\\b|\\bclientes\\b|funcionarios y empleados|prestamos? a (empleados|funcionarios|socios)",
                    ACTIVO_CIRCULANTE, false, "Es dinero que le deben a la empresa y se cobra en menos de un año."),

            // ---- Pasivo
            regla("anticipos? a proveedores", ACTIVO_CIRCULANTE, false,
                    "Se pagó por adelantado: el proveedor le debe la mercancía o el servicio a la empresa."),
            regla("(por pagar|prestamo|credito|acreedor|documentos|deuda|obligacion).*largo plazo|"
                            + "hipotec|obligaciones en circulacion|bonos por pagar|arrendamiento financiero",
                    PASIVO_LARGO, false, "Es una deuda que se paga en más de un año."),
            regla("por pagar|\\bproveedores\\b|\\bacreedor(es)?\\b|prestamos? bancarios?|creditos? bancarios?|"
                            + "iva trasladado|(impuestos?|isr|iva) retenid[oa]s?|retenciones|sobregiro|tarjetas? de credito",
                    PASIVO_CORTO, false, "Es una deuda (algo que la empresa debe) que se paga en menos de un año."),

            // ---- Capital
            regla("capital social|aportaciones?|prima (en|por) (la )?(venta|emision|suscripcion) de acciones|donaciones|"
                            + "capital (suscrito|exhibido|pagado)",
                    CAPITAL_CONTRIBUIDO, false, "Es lo que aportaron los dueños o socios."),
            regla("utilidad|utilidades|reserva legal|reservas? de capital|superavit|resultados? (del ejercicio|acumulados|de ejercicios anteriores)",
                    CAPITAL_GANADO, false, "Es lo que la empresa ha ganado y conserva."),

            // ---- Activo circulante
            regla("\\bcaja\\b|\\bbancos?\\b|cuenta de cheques|efectivo|inversiones? (temporales|en valores|a corto plazo)|"
                            + "inventario|almacen|mercancias|materias? primas?|productos? (terminados|en proceso)|"
                            + "iva (acreditable|por acreditar|pendiente)|anticipos? a proveedores|pagad[oa]s? por anticipado|"
                            + "papeleria|seguros|impuestos? a favor|isr a favor|fondo fijo",
                    ACTIVO_CIRCULANTE, false, "Es efectivo o algo que se cobra, se vende o se consume en menos de un año."),

            // ---- Activo no circulante
            regla("terreno|edificio|construccion|maquinaria|mobiliario|\\bequipos?\\b|vehiculo|automovil|camion|"
                            + "herramienta|patente|\\bmarcas?\\b|credito mercantil|franquicia|derechos? de autor|licencia|"
                            + "software|gastos de (instalacion|organizacion)|inversiones? (permanentes|en acciones|a largo plazo)|"
                            + "depositos? en garantia|intangible",
                    ACTIVO_NO_CIRCULANTE, false, "Es un bien o derecho que la empresa usa por más de un año.")
    );

    /** Sugerencia para el nombre de una cuenta, o vacío si no se reconoce. */
    public static Optional<Sugerencia> sugerir(String nombre) {
        if (nombre == null || nombre.isBlank()) return Optional.empty();
        String n = normalizar(nombre);
        for (Regla r : REGLAS) {
            if (r.patron.matcher(n).find()) {
                Clasificacion c = r.clasificacion;
                if (c == null) {   // cobros por anticipado: corto o largo plazo según el nombre
                    c = n.contains("largo plazo") ? PASIVO_LARGO : PASIVO_CORTO;
                }
                return Optional.of(new Sugerencia(c, r.resta, r.motivo));
            }
        }
        return Optional.empty();
    }

    /** minúsculas, sin acentos y con un solo espacio entre palabras */
    public static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9ñ ]", " ").trim().replaceAll("\\s+", " ");
    }
}
