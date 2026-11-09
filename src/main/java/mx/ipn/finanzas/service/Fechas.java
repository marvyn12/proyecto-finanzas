package mx.ipn.finanzas.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Textos de fechas para el balance: "Al 31 de diciembre de 2026". */
public final class Fechas {

    private Fechas() {}

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private static final DateTimeFormatter NORMAL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** "Al 31 de diciembre de 2026" (encabezado del balance) */
    public static String al(LocalDate f) {
        return f == null ? "" : "Al " + larga(f);
    }

    /** "31 de diciembre de 2026" */
    public static String larga(LocalDate f) {
        return f == null ? "" : f.getDayOfMonth() + " de " + MESES[f.getMonthValue() - 1] + " de " + f.getYear();
    }

    /**
     * Encabezado de un balance dinámico (por periodo): "Del 1 de enero al 31 de diciembre de 2026".
     * Si las dos fechas son iguales queda como estático: "Al 31 de diciembre de 2026".
     */
    public static String periodo(LocalDate d, LocalDate h) {
        if (d == null || h == null) return "";
        if (d.equals(h)) return al(h);
        String mesD = MESES[d.getMonthValue() - 1];
        if (d.getYear() == h.getYear() && d.getMonthValue() == h.getMonthValue()) {
            return "Del " + d.getDayOfMonth() + " al " + larga(h);
        }
        if (d.getYear() == h.getYear()) return "Del " + d.getDayOfMonth() + " de " + mesD + " al " + larga(h);
        return "Del " + larga(d) + " al " + larga(h);
    }

    /** Columnas del comparativo por periodo: "2026" y "2025" si son años completos; si no "01/01/26 – 30/06/26". */
    public static String[] etiquetas(LocalDate d1, LocalDate h1, LocalDate d2, LocalDate h2) {
        boolean anios = anioCompleto(d1, h1) && anioCompleto(d2, h2);
        if (anios) return new String[]{String.valueOf(h1.getYear()), String.valueOf(h2.getYear())};
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yy");
        return new String[]{d1.format(f) + " – " + h1.format(f), d2.format(f) + " – " + h2.format(f)};
    }

    private static boolean anioCompleto(LocalDate d, LocalDate h) {
        return d.getYear() == h.getYear() && d.getDayOfYear() == 1 && h.getMonthValue() == 12 && h.getDayOfMonth() == 31;
    }

    /** "31/12/2026" */
    public static String corta(LocalDate f) {
        return f == null ? "" : f.format(NORMAL);
    }

    /**
     * Encabezados de columna del comparativo: si las dos fechas son 31 de diciembre basta con el año
     * ("2026" y "2025"); si no, la fecha completa.
     */
    public static String[] etiquetas(LocalDate a, LocalDate b) {
        boolean cierres = a.getMonthValue() == 12 && a.getDayOfMonth() == 31
                && b.getMonthValue() == 12 && b.getDayOfMonth() == 31;
        if (cierres) return new String[]{String.valueOf(a.getYear()), String.valueOf(b.getYear())};
        return new String[]{corta(a), corta(b)};
    }

    /** Fecha con la que se abre una empresa: la de su último balance o, si no tiene, el cierre del año actual. */
    public static LocalDate porDefecto(LocalDate ultimoBalance, LocalDate hoy) {
        return ultimoBalance != null ? ultimoBalance : LocalDate.of(hoy.getYear(), 12, 31);
    }
}
