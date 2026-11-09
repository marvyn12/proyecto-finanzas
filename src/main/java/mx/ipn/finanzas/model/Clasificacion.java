package mx.ipn.finanzas.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Clasificación (subrubro) de cada cuenta dentro del balance general.
 * El orden en que están declaradas es el orden en que salen en el reporte.
 */
public enum Clasificacion {

    ACTIVO_CIRCULANTE(Seccion.ACTIVO, "Activo circulante", "Circulante", "Corto plazo",
            "Efectivo y lo que se cobra, se vende o se consume en menos de un año."),
    ACTIVO_NO_CIRCULANTE(Seccion.ACTIVO, "Activo no circulante", "No circulante", "Largo plazo",
            "Bienes y derechos que la empresa usa o cobra en más de un año (edificios, equipo, patentes…)."),
    PASIVO_CORTO(Seccion.PASIVO, "Pasivo a corto plazo", "A corto plazo (circulante)", "Corto plazo",
            "Deudas que se pagan en menos de un año."),
    PASIVO_LARGO(Seccion.PASIVO, "Pasivo a largo plazo", "A largo plazo (no circulante)", "Largo plazo",
            "Deudas que se pagan en más de un año."),
    CAPITAL_CONTRIBUIDO(Seccion.CAPITAL, "Capital contribuido", "Contribuido", null,
            "Lo que aportaron los dueños o socios."),
    CAPITAL_GANADO(Seccion.CAPITAL, "Capital ganado", "Ganado", null,
            "Lo que la empresa ha generado con sus resultados (utilidades, reservas).");

    private final Seccion seccion;
    private final String nombre;
    private final String encabezado;
    private final String plazo;
    private final String descripcion;

    Clasificacion(Seccion seccion, String nombre, String encabezado, String plazo, String descripcion) {
        this.seccion = seccion;
        this.nombre = nombre;
        this.encabezado = encabezado;
        this.plazo = plazo;
        this.descripcion = descripcion;
    }

    public String getCodigo() { return name(); }
    public Seccion getSeccion() { return seccion; }
    /** "Activo circulante" */
    public String getNombre() { return nombre; }
    /** Encabezado dentro de su sección en el reporte: "Circulante", "A corto plazo (circulante)"… */
    public String getEncabezado() { return encabezado; }
    /** "Corto plazo", "Largo plazo" o null (el capital no tiene plazo). */
    public String getPlazo() { return plazo; }
    public String getDescripcion() { return descripcion; }

    /** Texto del subtotal: "Total activo circulante" */
    public String getTotal() {
        return "Total " + nombre.toLowerCase();
    }

    /** Para los selectores: "Activo › Circulante (corto plazo)" */
    public String getEtiqueta() {
        return seccion.getNombre() + " › " + encabezado + (plazo != null && !encabezado.toLowerCase().contains("plazo")
                ? " (" + plazo.toLowerCase() + ")" : "");
    }

    public static List<Clasificacion> de(Seccion s) {
        List<Clasificacion> l = new ArrayList<>();
        for (Clasificacion c : values()) if (c.seccion == s) l.add(c);
        return l;
    }

    /** Acepta el código ("ACTIVO_CIRCULANTE") o el nombre ("Activo circulante"), sin importar mayúsculas ni acentos. */
    public static Clasificacion buscar(String texto) {
        if (texto == null) return null;
        String t = normal(texto);
        for (Clasificacion c : values()) {
            if (normal(c.name()).equals(t) || normal(c.nombre).equals(t)) return c;
        }
        return null;
    }

    private static String normal(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('_', ' ').trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
