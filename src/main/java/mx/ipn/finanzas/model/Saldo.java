package mx.ipn.finanzas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import mx.ipn.finanzas.dto.Formato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Una cuenta registrada en el balance de cierta fecha, con su importe.
 * Guarda una copia del nombre y la clasificación: si luego quitas la cuenta del catálogo, el balance no cambia.
 */
@Entity
@Table(name = "bg_saldo")
public class Saldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    /** Fecha del balance ("Al 31 de diciembre de 2026"). */
    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Clasificacion clasificacion;

    /** true = complementaria: su importe se resta. */
    @Column(nullable = false)
    private boolean resta;

    /** Siempre positivo o cero; "resta" dice si se suma o se resta. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal importe;

    @Column(length = 255)
    private String comentario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Clasificacion getClasificacion() { return clasificacion; }
    public void setClasificacion(Clasificacion clasificacion) { this.clasificacion = clasificacion; }

    public boolean isResta() { return resta; }
    public void setResta(boolean resta) { this.resta = resta; }

    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    // ---------------------------------------------------------------- calculados (no se guardan)

    /** Importe con signo: negativo si la cuenta resta. */
    public BigDecimal getValor() {
        BigDecimal v = importe == null ? BigDecimal.ZERO : importe;
        return resta ? v.negate() : v;
    }

    /** $1,500.00 o ($1,500.00) si resta */
    public String getImporteTexto() {
        return Formato.pesos(getValor());
    }

    public boolean isEnCero() {
        return importe == null || importe.signum() == 0;
    }

    /** 31/12/2026 */
    public String getFechaTexto() {
        return fecha == null ? "" : fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
