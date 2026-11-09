package mx.ipn.finanzas.dto;

/**
 * Una fila del reporte.
 * estilo: seccion | grupo | detalle | subtotal | total
 * celdas: arreglo con una posición por columna; null = columna vacía en esa fila.
 */
public class Linea {

    private final String concepto;
    private final String estilo;
    private final int sangria;
    private final Celda[] celdas;
    private final String comentario;

    public Linea(String concepto, String estilo, int sangria, Celda[] celdas, String comentario) {
        this.concepto = concepto;
        this.estilo = estilo;
        this.sangria = sangria;
        this.celdas = celdas;
        this.comentario = comentario;
    }

    public String getConcepto() { return concepto; }
    public String getEstilo() { return estilo; }
    public int getSangria() { return sangria; }
    public Celda[] getCeldas() { return celdas; }
    public String getComentario() { return comentario; }
}
