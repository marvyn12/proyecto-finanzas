package mx.ipn.finanzas.dto;

import java.io.Serializable;
import java.time.LocalDate;

/** Lo que estás viendo ahorita (empresa y fecha del balance). Se guarda en la sesión del navegador. */
public class Seleccion implements Serializable {

    private static final long serialVersionUID = 3L;

    private Long empresaId;
    private boolean dinamico;       // false = estático (a una fecha), true = dinámico (por periodo)
    private LocalDate inicio;       // solo en dinámico
    private LocalDate fecha;        // fecha del balance (en dinámico: fin del periodo)
    private boolean comparar;
    private LocalDate inicio2;
    private LocalDate fecha2;

    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public boolean isComparar() { return comparar; }
    public void setComparar(boolean comparar) { this.comparar = comparar; }
    public LocalDate getFecha2() { return fecha2; }
    public boolean isDinamico() { return dinamico; }
    public void setDinamico(boolean dinamico) { this.dinamico = dinamico; }
    public LocalDate getInicio() { return inicio; }
    public void setInicio(LocalDate inicio) { this.inicio = inicio; }
    public LocalDate getInicio2() { return inicio2; }
    public void setInicio2(LocalDate inicio2) { this.inicio2 = inicio2; }
    public void setFecha2(LocalDate fecha2) { this.fecha2 = fecha2; }
}
