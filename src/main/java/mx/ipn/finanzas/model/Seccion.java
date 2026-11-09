package mx.ipn.finanzas.model;

/**
 * Las tres partes del balance general.
 * Ecuación contable: ACTIVO = PASIVO + CAPITAL CONTABLE  (o bien  ACTIVO − PASIVO = CAPITAL CONTABLE).
 */
public enum Seccion {

    ACTIVO("Activo", "Total activo", "chip-activo",
            "Lo que la empresa tiene: bienes y derechos."),
    PASIVO("Pasivo", "Total pasivo", "chip-pasivo",
            "Lo que la empresa debe a terceros."),
    CAPITAL("Capital contable", "Total capital contable", "chip-capital",
            "Lo que es de los dueños: Activo − Pasivo.");

    private final String nombre;
    private final String total;
    private final String chip;
    private final String descripcion;

    Seccion(String nombre, String total, String chip, String descripcion) {
        this.nombre = nombre;
        this.total = total;
        this.chip = chip;
        this.descripcion = descripcion;
    }

    public String getCodigo() { return name(); }
    public String getNombre() { return nombre; }
    /** Texto del renglón de total, p. ej. "Total activo" */
    public String getTotal() { return total; }
    /** Clase CSS de la etiqueta de color en pantalla. */
    public String getChip() { return chip; }
    public String getDescripcion() { return descripcion; }
}
