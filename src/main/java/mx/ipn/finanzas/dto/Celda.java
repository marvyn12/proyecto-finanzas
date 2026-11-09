package mx.ipn.finanzas.dto;

import java.math.BigDecimal;

/** Un importe dentro de una columna del reporte, con su signo de pesos y su raya (simple o doble). */
public class Celda {

    private final BigDecimal importe;
    private final boolean pesos;
    private final String subrayado;    // null, "simple" o "doble"

    public Celda(BigDecimal importe, boolean pesos, String subrayado) {
        this.importe = Formato.redondear(importe);
        this.pesos = pesos;
        this.subrayado = subrayado;
    }

    public BigDecimal getImporte() { return importe; }
    public boolean isPesos() { return pesos; }
    public String getSubrayado() { return subrayado; }
    public boolean isNegativo() { return importe.signum() < 0; }

    /** 1,234.56 · (1,234.56) si es negativo · "—" si es cero (el balance no muestra ceros). */
    public String getTexto() {
        return importe.signum() == 0 ? "—" : Formato.importe(importe);
    }
}
