package mx.ipn.finanzas.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Balance general ya "armado" en formato contable clásico (3 columnas escalonadas), con sus totales,
 * la validación de la ecuación contable y los avisos. La pantalla y el PDF dibujan las mismas líneas.
 */
public class BalanceGeneral {

    private String empresa;
    private String titulo = "Balance general";
    private boolean dinamico;        // false = a una fecha, true = por periodo
    private String inicio;           // ISO, solo en dinámico
    private int registros;           // capturas que entraron (en dinámico se suman las de la misma cuenta)
    private int numFechas;           // cuántas fechas distintas entraron
    private String fecha;            // ISO: 2026-12-31 (en dinámico, fin del periodo)
    private String fechaTexto;       // "Al 31 de diciembre de 2026" o "Del 1 de enero al 31 de diciembre de 2026"
    private String elaborado;        // "8 de noviembre de 2026" (día en que se generó)
    private int numColumnas = 3;
    private final List<Linea> lineas = new ArrayList<>();

    private BigDecimal totalActivo = BigDecimal.ZERO;
    private BigDecimal totalPasivo = BigDecimal.ZERO;
    private BigDecimal totalCapital = BigDecimal.ZERO;
    private BigDecimal activoCirculante = BigDecimal.ZERO;
    private BigDecimal pasivoCorto = BigDecimal.ZERO;

    private int numCuentas;                                   // registradas (incluye las de $0)
    private final List<String> omitidas = new ArrayList<>();   // cuentas en $0 que no se muestran
    private final List<Suma> sumas = new ArrayList<>();        // suma por clasificación
    private final List<String> advertencias = new ArrayList<>();
    private final List<String> pistas = new ArrayList<>();     // por qué no cuadra

    // ================================================================== Ecuación contable

    public BigDecimal getTotalPasivoCapital() { return totalPasivo.add(totalCapital); }

    /** Activo − (Pasivo + Capital). Cero = cuadra. */
    public BigDecimal getDiferencia() { return totalActivo.subtract(getTotalPasivoCapital()); }

    /** ¿Cumple Activo = Pasivo + Capital contable? */
    public boolean isCuadra() { return getDiferencia().signum() == 0; }

    /** Activo − Pasivo (lo que debería ser el capital contable). */
    public BigDecimal getCapitalCalculado() { return totalActivo.subtract(totalPasivo); }

    public boolean isVacio() { return lineas.isEmpty(); }

    // ================================================================== Textos para la pantalla

    public String getTotalActivoTexto() { return Formato.pesos(totalActivo); }
    public String getTotalPasivoTexto() { return Formato.pesos(totalPasivo); }
    public String getTotalCapitalTexto() { return Formato.pesos(totalCapital); }
    public String getTotalPasivoCapitalTexto() { return Formato.pesos(getTotalPasivoCapital()); }
    public String getDiferenciaTexto() { return Formato.pesos(getDiferencia().abs()); }
    public String getCapitalCalculadoTexto() { return Formato.pesos(getCapitalCalculado()); }
    public boolean isCapitalNegativo() { return totalCapital.signum() < 0; }

    /** Capital de trabajo = Activo circulante − Pasivo a corto plazo */
    public String getCapitalTrabajoTexto() { return Formato.pesos(activoCirculante.subtract(pasivoCorto)); }

    /** Razón circulante = Activo circulante ÷ Pasivo a corto plazo */
    public String getRazonCirculanteTexto() { return Formato.razon(activoCirculante, pasivoCorto); }

    /** Endeudamiento = Pasivo ÷ Activo */
    public String getEndeudamientoTexto() { return Formato.porcentaje(totalPasivo, totalActivo); }

    public int getNumMostradas() { return numCuentas - omitidas.size(); }

    // ================================================================== Getters y setters

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getTitulo() { return titulo; }
    public boolean isDinamico() { return dinamico; }
    public void setDinamico(boolean dinamico) { this.dinamico = dinamico; }
    public String getInicio() { return inicio; }
    public void setInicio(String inicio) { this.inicio = inicio; }
    public int getRegistros() { return registros; }
    public void setRegistros(int registros) { this.registros = registros; }
    public int getNumFechas() { return numFechas; }
    public void setNumFechas(int numFechas) { this.numFechas = numFechas; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getFechaTexto() { return fechaTexto; }
    public void setFechaTexto(String fechaTexto) { this.fechaTexto = fechaTexto; }
    public String getElaborado() { return elaborado; }
    public void setElaborado(String elaborado) { this.elaborado = elaborado; }
    public int getNumColumnas() { return numColumnas; }
    public List<Linea> getLineas() { return lineas; }
    public BigDecimal getTotalActivo() { return totalActivo; }
    public void setTotalActivo(BigDecimal v) { this.totalActivo = v; }
    public BigDecimal getTotalPasivo() { return totalPasivo; }
    public void setTotalPasivo(BigDecimal v) { this.totalPasivo = v; }
    public BigDecimal getTotalCapital() { return totalCapital; }
    public void setTotalCapital(BigDecimal v) { this.totalCapital = v; }
    public BigDecimal getActivoCirculante() { return activoCirculante; }
    public void setActivoCirculante(BigDecimal v) { this.activoCirculante = v; }
    public BigDecimal getPasivoCorto() { return pasivoCorto; }
    public void setPasivoCorto(BigDecimal v) { this.pasivoCorto = v; }
    public int getNumCuentas() { return numCuentas; }
    public void setNumCuentas(int numCuentas) { this.numCuentas = numCuentas; }
    public List<String> getOmitidas() { return omitidas; }
    public List<Suma> getSumas() { return sumas; }
    public List<String> getAdvertencias() { return advertencias; }
    public List<String> getPistas() { return pistas; }

    /** Suma de una clasificación (módulo 3: cálculo automático). */
    public static class Suma {
        private final String seccion;
        private final String clasificacion;
        private final String plazo;
        private final BigDecimal total;
        private final int cuentas;
        private final boolean esTotal;

        public Suma(String seccion, String clasificacion, String plazo, BigDecimal total, int cuentas, boolean esTotal) {
            this.seccion = seccion;
            this.clasificacion = clasificacion;
            this.plazo = plazo;
            this.total = Formato.redondear(total);
            this.cuentas = cuentas;
            this.esTotal = esTotal;
        }

        public String getSeccion() { return seccion; }
        public String getClasificacion() { return clasificacion; }
        public String getPlazo() { return plazo; }
        public BigDecimal getTotal() { return total; }
        public String getTotalTexto() { return Formato.pesos(total); }
        public int getCuentas() { return cuentas; }
        public boolean isEsTotal() { return esTotal; }
    }
}
