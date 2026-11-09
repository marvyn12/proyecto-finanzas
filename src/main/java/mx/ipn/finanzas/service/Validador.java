package mx.ipn.finanzas.service;

import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.Empresa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Todas las validaciones de datos que escribe el usuario.
 * Si algo está mal lanzan IllegalArgumentException con un mensaje listo para mostrar en pantalla.
 * Son estáticas y no usan la base de datos, por eso se pueden probar con JUnit directo (ValidadorTest).
 */
public final class Validador {

    private Validador() {}

    public static final int MAX_NOMBRE = 150;
    public static final int MAX_COMENTARIO = 255;
    public static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("9999999999999.99");
    public static final int ANIO_MINIMO = 1900;
    public static final int ANIO_MAXIMO = 2100;
    private static final Set<String> ORDENES = Set.of(Empresa.ORDEN_CATALOGO, Empresa.ORDEN_ALFABETICO, Empresa.ORDEN_CAPTURA);

    /** Texto obligatorio: quita espacios de sobra y revisa el largo. */
    public static String texto(String valor, String que, int maximo) {
        String limpio = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) throw new IllegalArgumentException("Escribe " + que + ".");
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El texto de " + que + " es muy largo (máximo " + maximo + " caracteres).");
        }
        if (!limpio.matches(".*[\\p{L}\\p{N}].*")) {
            throw new IllegalArgumentException("Revisa " + que + ": debe llevar letras o números.");
        }
        return limpio;
    }

    /** Comentario opcional: null si viene vacío. */
    public static String comentario(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim().replaceAll("\\s+", " ");
        if (limpio.length() > MAX_COMENTARIO) {
            throw new IllegalArgumentException("El comentario es muy largo (máximo " + MAX_COMENTARIO + " caracteres).");
        }
        return limpio;
    }

    /** Fecha en formato ISO (como la manda el calendario del navegador): 2026-12-31 */
    public static LocalDate fecha(String fecha) {
        if (fecha == null || fecha.isBlank()) throw new IllegalArgumentException("Escribe la fecha del balance.");
        LocalDate f;
        try {
            f = LocalDate.parse(fecha.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La fecha \"" + fecha.trim() + "\" no es válida.");
        }
        if (f.getYear() < ANIO_MINIMO || f.getYear() > ANIO_MAXIMO) {
            throw new IllegalArgumentException("Revisa el año de la fecha (" + f.getYear() + "): debe estar entre "
                    + ANIO_MINIMO + " y " + ANIO_MAXIMO + ".");
        }
        return f;
    }

    /**
     * Importe en pesos. Llega ya limpio desde la página (p. ej. "1500.75"); aquí se revisa otra vez
     * por si alguien manda datos sin pasar por la página.
     * Se permite $0.00: esas cuentas se guardan pero no salen en el balance (requisito "omitir ceros").
     */
    public static BigDecimal importe(String importe) {
        if (importe == null || importe.isBlank()) throw new IllegalArgumentException("Escribe el importe.");
        String s = importe.trim().replace("$", "").replace(",", "").replace(" ", "");
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El importe \"" + importe.trim() + "\" no es un número válido.");
        }
        if (v.signum() < 0) {
            throw new IllegalArgumentException("El importe va sin signo menos. Si la cuenta resta (depreciación, "
                    + "estimación, pérdida…), regístrala como cuenta complementaria.");
        }
        if (v.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("El importe lleva máximo 2 decimales (centavos).");
        if (v.compareTo(IMPORTE_MAXIMO) > 0) throw new IllegalArgumentException("El importe es demasiado grande (máximo $9,999,999,999,999.99).");
        return v.setScale(2);
    }

    public static Clasificacion clasificacion(String texto) {
        Clasificacion c = Clasificacion.buscar(texto);
        if (c == null) throw new IllegalArgumentException("Elige la clasificación de la cuenta (activo, pasivo o capital).");
        return c;
    }

    public static String orden(String orden) {
        if (orden == null || !ORDENES.contains(orden)) throw new IllegalArgumentException("Elige cómo ordenar las cuentas.");
        return orden;
    }

    /** "1", "true", "on" (casilla marcada) = true */
    public static boolean casilla(String valor) {
        return valor != null && (valor.equals("1") || valor.equalsIgnoreCase("true") || valor.equalsIgnoreCase("on"));
    }
}
