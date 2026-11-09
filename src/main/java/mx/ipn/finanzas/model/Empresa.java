package mx.ipn.finanzas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una empresa: su nombre y cómo ordenar las cuentas dentro de cada clasificación. */
@Entity
@Table(name = "bg_empresa")
public class Empresa {

    public static final String ORDEN_CATALOGO = "CATALOGO";
    public static final String ORDEN_ALFABETICO = "ALFABETICO";
    public static final String ORDEN_CAPTURA = "CAPTURA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** CATALOGO (orden de liquidez / exigibilidad del catálogo), ALFABETICO o CAPTURA */
    @Column(nullable = false, length = 20)
    private String ordenCuentas = ORDEN_CATALOGO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getOrdenCuentas() { return ordenCuentas; }
    public void setOrdenCuentas(String ordenCuentas) { this.ordenCuentas = ordenCuentas; }

    /** Texto para mostrar en pantalla (no se guarda). */
    public String getOrdenTexto() {
        if (ORDEN_ALFABETICO.equals(ordenCuentas)) return "alfabético";
        if (ORDEN_CAPTURA.equals(ordenCuentas)) return "fecha de captura";
        return "catálogo";
    }
}
