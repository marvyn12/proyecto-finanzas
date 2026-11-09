package mx.ipn.finanzas.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formato de cifras: 1,234.56 y negativos entre paréntesis, como en contabilidad. */
public final class Formato {

    private Formato() {}

    private static final DecimalFormatSymbols US = DecimalFormatSymbols.getInstance(Locale.US);

    public static BigDecimal redondear(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 1,234.56 o (1,234.56) */
    public static String importe(BigDecimal v) {
        BigDecimal r = redondear(v);
        String abs = new DecimalFormat("#,##0.00", US).format(r.abs());
        return r.signum() < 0 ? "(" + abs + ")" : abs;
    }

    /** $1,234.56 o ($1,234.56) */
    public static String pesos(BigDecimal v) {
        BigDecimal r = redondear(v);
        String abs = "$" + new DecimalFormat("#,##0.00", US).format(r.abs());
        return r.signum() < 0 ? "(" + abs + ")" : abs;
    }

    /** a ÷ b con 2 decimales, p. ej. "1.85"; "—" si b es cero o negativo. */
    public static String razon(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.signum() <= 0) return "—";
        return new DecimalFormat("#,##0.00", US).format(a.divide(b, 2, RoundingMode.HALF_UP));
    }

    /** parte / total como "10.4 %"; "—" si el total es cero. */
    public static String porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return "—";
        BigDecimal p = parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        return new DecimalFormat("0.0", US).format(p) + " %";
    }

    /** Variación de b a a: "+12.5 %", "−3.0 %"; "—" si b es cero. */
    public static String variacion(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) return "—";
        BigDecimal p = a.subtract(b).multiply(BigDecimal.valueOf(100)).divide(b.abs(), 1, RoundingMode.HALF_UP);
        String txt = new DecimalFormat("0.0", US).format(p.abs()) + " %";
        if (p.signum() > 0) return "+" + txt;
        if (p.signum() < 0) return "−" + txt;
        return txt;
    }
}
