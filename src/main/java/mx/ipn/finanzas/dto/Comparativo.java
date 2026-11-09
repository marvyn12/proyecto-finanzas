package mx.ipn.finanzas.dto;

import java.util.ArrayList;
import java.util.List;

/** Balance general comparativo: dos fechas lado a lado, con la variación en pesos y en porcentaje. */
public class Comparativo {

    private String empresa;
    private String titulo = "Balance general comparativo";
    private String etiquetaActual;      // "2026" o "31/12/2026"
    private String etiquetaAnterior;
    private String fechaActualTexto;    // "Al 31 de diciembre de 2026"
    private String fechaAnteriorTexto;
    private String fechaActual;         // ISO
    private String fechaAnterior;
    private boolean cuadraActual;
    private boolean cuadraAnterior;
    private String diferenciaActual;    // "$0.00"
    private String diferenciaAnterior;
    private final List<Fila> filas = new ArrayList<>();

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getTitulo() { return titulo; }
    public String getEtiquetaActual() { return etiquetaActual; }
    public void setEtiquetaActual(String v) { this.etiquetaActual = v; }
    public String getEtiquetaAnterior() { return etiquetaAnterior; }
    public void setEtiquetaAnterior(String v) { this.etiquetaAnterior = v; }
    public String getFechaActualTexto() { return fechaActualTexto; }
    public void setFechaActualTexto(String v) { this.fechaActualTexto = v; }
    public String getFechaAnteriorTexto() { return fechaAnteriorTexto; }
    public void setFechaAnteriorTexto(String v) { this.fechaAnteriorTexto = v; }
    public String getFechaActual() { return fechaActual; }
    public void setFechaActual(String v) { this.fechaActual = v; }
    public String getFechaAnterior() { return fechaAnterior; }
    public void setFechaAnterior(String v) { this.fechaAnterior = v; }
    public boolean isCuadraActual() { return cuadraActual; }
    public void setCuadraActual(boolean v) { this.cuadraActual = v; }
    public boolean isCuadraAnterior() { return cuadraAnterior; }
    public void setCuadraAnterior(boolean v) { this.cuadraAnterior = v; }
    public String getDiferenciaActual() { return diferenciaActual; }
    public void setDiferenciaActual(String v) { this.diferenciaActual = v; }
    public String getDiferenciaAnterior() { return diferenciaAnterior; }
    public void setDiferenciaAnterior(String v) { this.diferenciaAnterior = v; }
    public List<Fila> getFilas() { return filas; }

    /**
     * Un renglón: concepto, importe en cada fecha, variación en pesos y en porcentaje.
     * En los encabezados (estilo seccion o grupo) las celdas vienen en null.
     */
    public static class Fila {
        private final String concepto;
        private final String estilo;
        private final int sangria;
        private final Celda actual;
        private final Celda anterior;
        private final Celda variacion;
        private final String porcentaje;

        public Fila(String concepto, String estilo, int sangria, Celda actual, Celda anterior,
                    Celda variacion, String porcentaje) {
            this.concepto = concepto;
            this.estilo = estilo;
            this.sangria = sangria;
            this.actual = actual;
            this.anterior = anterior;
            this.variacion = variacion;
            this.porcentaje = porcentaje;
        }

        public String getConcepto() { return concepto; }
        public String getEstilo() { return estilo; }
        public int getSangria() { return sangria; }
        public Celda getActual() { return actual; }
        public Celda getAnterior() { return anterior; }
        public Celda getVariacion() { return variacion; }
        public String getPorcentaje() { return porcentaje; }
        public boolean isEncabezado() { return actual == null; }
    }
}
