package mx.ipn.finanzas.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cuentas")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String clasificacion; // Activo, Pasivo, Capital
    private String subclasificacion; // Circulante, No Circulante
    private Double importe;

    // Constructor vacío (obligatorio para JPA)
    public Cuenta() {
    }

    // Constructor con parámetros
    public Cuenta(String nombre, String clasificacion, String subclasificacion, Double importe) {
        this.nombre = nombre;
        this.clasificacion = clasificacion;
        this.subclasificacion = subclasificacion;
        this.importe = importe;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getClasificacion() { return clasificacion; }
    public void setClasificacion(String clasificacion) { this.clasificacion = clasificacion; }

    public String getSubclasificacion() { return subclasificacion; }
    public void setSubclasificacion(String subclasificacion) { this.subclasificacion = subclasificacion; }

    public Double getImporte() { return importe; }
    public void setImporte(Double importe) { this.importe = importe; }
}