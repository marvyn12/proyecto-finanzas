package mx.ipn.finanzas.dto;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Estado de resultados ya "armado" en formato contable clásico (4 columnas).
 * El HTML (Thymeleaf) y el PDF (jsPDF) dibujan exactamente las mismas líneas,
 * así la lógica del formato vive en un solo lugar: CuentaService.
 */
public class ReporteFinanciero {

    private final EstadoFinanciero estadoResultados;
    private final double ventasNetas;
    private final double costoVendido;
    private final double utilidadBruta;
    private final double utilidadOperacion;
    private final double utilidadAntesImpuestos;
    private final double utilidadNeta;
    private final double tasaIsr;
    private final double tasaPtu;

    public ReporteFinanciero(EstadoFinanciero estadoResultados, double ventasNetas, double costoVendido,
                             double utilidadBruta, double utilidadOperacion, double utilidadAntesImpuestos,
                             double utilidadNeta, double tasaIsr, double tasaPtu) {
        this.estadoResultados = estadoResultados;
        this.ventasNetas = ventasNetas;
        this.costoVendido = costoVendido;
        this.utilidadBruta = utilidadBruta;
        this.utilidadOperacion = utilidadOperacion;
        this.utilidadAntesImpuestos = utilidadAntesImpuestos;
        this.utilidadNeta = utilidadNeta;
        this.tasaIsr = tasaIsr;
        this.tasaPtu = tasaPtu;
    }

    public EstadoFinanciero getEstadoResultados() { return estadoResultados; }
    public double getVentasNetas() { return ventasNetas; }
    public double getCostoVendido() { return costoVendido; }
    public double getUtilidadBruta() { return utilidadBruta; }
    public double getUtilidadOperacion() { return utilidadOperacion; }
    public double getUtilidadAntesImpuestos() { return utilidadAntesImpuestos; }
    public double getUtilidadNeta() { return utilidadNeta; }
    public double getTasaIsr() { return tasaIsr; }
    public double getTasaPtu() { return tasaPtu; }

    /** Tasas como porcentaje sin ceros de sobra: 0.33 -> "33", 0.305 -> "30.5" */
    public String getTasaIsrTexto() { return porcentaje(tasaIsr); }
    public String getTasaPtuTexto() { return porcentaje(tasaPtu); }

    /** Margen neto = utilidad neta / ventas netas */
    public String getMargenNetoTexto() {
        if (Math.abs(ventasNetas) < 0.005) return "—";
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US))
                .format(utilidadNeta / ventasNetas * 100) + " %";
    }

    public static String porcentaje(double tasa) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(tasa * 100);
    }

    /** Un estado financiero: título + líneas escalonadas en N columnas de importes. */
    public static class EstadoFinanciero {
        private final String tipo;
        private final String titulo;
        private final int numColumnas;
        private final List<Linea> lineas = new ArrayList<>();

        public EstadoFinanciero(String tipo, String titulo, int numColumnas) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.numColumnas = numColumnas;
        }

        public String getTipo() { return tipo; }
        public String getTitulo() { return titulo; }
        public int getNumColumnas() { return numColumnas; }
        public List<Linea> getLineas() { return lineas; }
    }

    /**
     * Una fila del estado.
     * estilo: grupo | detalle | resultado | total
     * celdas: arreglo de tamaño numColumnas; null = columna vacía en esa fila.
     */
    public static class Linea {
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

    /** Un importe dentro de una columna, con su signo de pesos y su raya (simple o doble). */
    public static class Celda {
        private static final DecimalFormat FORMATO =
                new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

        private final double importe;
        private final String texto;
        private final boolean pesos;
        private final String subrayado;    // null, "simple" o "doble"

        public Celda(double importe, boolean pesos, String subrayado) {
            this.importe = importe;
            this.texto = formatear(importe);
            this.pesos = pesos;
            this.subrayado = subrayado;
        }

        /** Negativos entre paréntesis, como se acostumbra en contabilidad. */
        public static String formatear(double valor) {
            double redondeado = Math.round(valor * 100.0) / 100.0;
            if (redondeado == 0) redondeado = 0; // evita "-0.00"
            String abs;
            synchronized (FORMATO) {
                abs = FORMATO.format(Math.abs(redondeado));
            }
            return redondeado < 0 ? "(" + abs + ")" : abs;
        }

        public double getImporte() { return importe; }
        public String getTexto() { return texto; }
        public boolean isPesos() { return pesos; }
        public String getSubrayado() { return subrayado; }
        public boolean isNegativo() { return Math.round(importe * 100.0) < 0; }
    }
}