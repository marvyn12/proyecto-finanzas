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

/** Una cuenta del catálogo de una empresa (lo que aparece en el selector "Cuenta"). */
@Entity
@Table(name = "bg_catalogo")
public class CuentaCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Clasificacion clasificacion;

    /** true = cuenta complementaria: resta dentro de su clasificación (depreciación acumulada, pérdidas…). */
    @Column(nullable = false)
    private boolean resta;

    /** Posición dentro del catálogo (para el orden "Catálogo"). */
    @Column(nullable = false)
    private int orden;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Clasificacion getClasificacion() { return clasificacion; }
    public void setClasificacion(Clasificacion clasificacion) { this.clasificacion = clasificacion; }

    public boolean isResta() { return resta; }
    public void setResta(boolean resta) { this.resta = resta; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
}
