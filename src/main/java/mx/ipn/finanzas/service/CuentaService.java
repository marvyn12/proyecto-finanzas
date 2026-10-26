package mx.ipn.finanzas.service;

import mx.ipn.finanzas.dto.ReporteFinanciero;
import mx.ipn.finanzas.dto.ReporteFinanciero.Celda;
import mx.ipn.finanzas.dto.ReporteFinanciero.EstadoFinanciero;
import mx.ipn.finanzas.dto.ReporteFinanciero.Linea;
import mx.ipn.finanzas.model.Cuenta;
import mx.ipn.finanzas.repository.CuentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CuentaService {

    // ------------------------------------------------ Secciones (se guardan en "clasificacion")
    public static final String SEC_VENTAS = "Ventas";
    public static final String SEC_COSTO = "Costo de ventas";
    public static final String SEC_OPERACION = "Gastos de operación";
    public static final String SEC_OTROS = "Otros gastos y productos";

    // ------------------------------------------------ Rubros (se guardan en "subclasificacion")
    public static final String VENTAS = "Ventas";
    public static final String DEV_VENTAS = "Devoluciones y descuentos sobre ventas";
    public static final String INV_INICIAL = "Inventario inicial";
    public static final String COMPRAS = "Compras";
    public static final String GASTOS_COMPRA = "Gastos de compra";
    public static final String DEV_COMPRAS = "Devoluciones y descuentos sobre compras";
    public static final String INV_FINAL = "Inventario final";
    public static final String GASTOS_VENTA = "Gastos de venta";
    public static final String GASTOS_ADMIN = "Gastos de administración";
    public static final String PROD_FINANCIEROS = "Productos financieros";
    public static final String GASTOS_FINANCIEROS = "Gastos financieros";
    public static final String OTROS_GASTOS = "Otros gastos";
    public static final String OTROS_PRODUCTOS = "Otros productos";

    /** Sección -> rubros, en el orden en que aparecen en el estado de resultados. */
    public static final Map<String, List<String>> RUBROS;
    /** Color de la etiqueta de cada sección en la tabla de movimientos. */
    public static final Map<String, String> COLORES;

    static {
        Map<String, List<String>> r = new LinkedHashMap<>();
        r.put(SEC_VENTAS, List.of(VENTAS, DEV_VENTAS));
        r.put(SEC_COSTO, List.of(INV_INICIAL, COMPRAS, GASTOS_COMPRA, DEV_COMPRAS, INV_FINAL));
        r.put(SEC_OPERACION, List.of(GASTOS_VENTA, GASTOS_ADMIN, PROD_FINANCIEROS, GASTOS_FINANCIEROS));
        r.put(SEC_OTROS, List.of(OTROS_GASTOS, OTROS_PRODUCTOS));
        RUBROS = Collections.unmodifiableMap(r);

        Map<String, String> c = new LinkedHashMap<>();
        c.put(SEC_VENTAS, "chip-ventas");
        c.put(SEC_COSTO, "chip-costo");
        c.put(SEC_OPERACION, "chip-operacion");
        c.put(SEC_OTROS, "chip-otros");
        COLORES = Collections.unmodifiableMap(c);
    }

    // Estilos de línea
    private static final String GRUPO = "grupo";
    private static final String DETALLE = "detalle";
    private static final String RESULTADO = "resultado";
    private static final String TOTAL = "total";

    // Rayas
    private static final String SIMPLE = "simple";
    private static final String DOBLE = "doble";

    @Autowired
    private CuentaRepository cuentaRepository;

    // Tasas de ISR y PTU. Valores por defecto = ejemplo 3; se pueden cambiar en application.properties
    // (finanzas.tasa-isr=0.30) o desde la página.
    @Value("${finanzas.tasa-isr:0.33}")
    private double tasaIsr = 0.33;

    @Value("${finanzas.tasa-ptu:0.10}")
    private double tasaPtu = 0.10;

    // ------------------------------------------------------------------ CRUD

    public List<Cuenta> obtenerTodas() {
        List<Cuenta> cuentas = new ArrayList<>(cuentaRepository.findAll());
        cuentas.sort(Comparator.comparing(Cuenta::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return cuentas;
    }

    /** Guarda el movimiento; la sección se asigna sola según el rubro. */
    public Cuenta guardarCuenta(Cuenta cuenta) {
        String rubro = normalizarRubro(cuenta.getSubclasificacion());
        if (rubro == null) throw new IllegalArgumentException("Rubro no válido: " + cuenta.getSubclasificacion());
        cuenta.setNombre(cuenta.getNombre().trim());
        cuenta.setSubclasificacion(rubro);
        cuenta.setClasificacion(seccionDe(rubro));
        if (cuenta.getComentarios() != null && cuenta.getComentarios().isBlank()) {
            cuenta.setComentarios(null);
        }
        return cuentaRepository.save(cuenta);
    }

    public void eliminarCuenta(Long id) {
        cuentaRepository.deleteById(id);
    }

    public void reiniciarEjercicio() {
        cuentaRepository.deleteAll();
    }

    public boolean esRubroValido(String rubro) {
        return normalizarRubro(rubro) != null;
    }

    /** Movimientos viejos (p. ej. de Activo o Pasivo) que no entran al estado de resultados. */
    public Set<Long> idsFueraDelEstado(List<Cuenta> cuentas) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Cuenta c : cuentas) {
            if (normalizarRubro(c.getSubclasificacion()) == null) ids.add(c.getId());
        }
        return ids;
    }

    public void actualizarTasas(double isr, double ptu) {
        this.tasaIsr = isr;
        this.tasaPtu = ptu;
    }

    // -------------------------------------------------------------- Reporte

    public ReporteFinanciero generarReporte() {
        List<Agrupada> cuentas = agrupar(obtenerTodas());
        Totales t = new Totales(cuentas, tasaIsr, tasaPtu);
        return new ReporteFinanciero(construirEstadoResultados(cuentas, t),
                t.ventasNetas, t.costoVendido, t.utilidadBruta, t.utilidadOperacion,
                t.utilidadAntesImpuestos, t.utilidadNeta, tasaIsr, tasaPtu);
    }

    /**
     * Estado de resultados pormenorizado en 4 columnas (como el ejemplo 3):
     * col 0 = detalle de cuentas, col 1 = subtotales de grupo, col 2 = totales parciales, col 3 = resultados.
     */
    private EstadoFinanciero construirEstadoResultados(List<Agrupada> todas, Totales t) {
        Constructor b = new Constructor("ER", "Estado de Resultados", 4);

        // ---------------- Ventas
        List<Agrupada> ventas = filtrar(todas, VENTAS);
        List<Agrupada> devVentas = filtrar(todas, DEV_VENTAS);
        if (devVentas.isEmpty()) {
            if (ventas.size() > 1) {
                b.titulo("Ventas netas", GRUPO, 0);
                detalle(b, ventas, 1, 2, 3, null, null, null);
            } else {
                b.fila("Ventas netas", RESULTADO, 0, comentarios(ventas), m(3, t.ventasNetas, null));
            }
        } else {
            if (ventas.size() > 1) {
                b.titulo("Ventas totales", GRUPO, 0);
                detalle(b, ventas, 1, 1, 2, null, null, null);
            } else {
                b.fila("Ventas totales", DETALLE, 0, comentarios(ventas), m(2, t.ventas, null));
            }
            detalle(b, devVentas, 1, 1, 2, SIMPLE, null, null);
            b.fila("Ventas netas", RESULTADO, 0, null, m(3, t.ventasNetas, null));
        }

        // ---------------- Costo de lo vendido (procedimiento analítico)
        List<Agrupada> invInicial = filtrar(todas, INV_INICIAL);
        List<Agrupada> compras = new ArrayList<>(filtrar(todas, COMPRAS));
        compras.addAll(filtrar(todas, GASTOS_COMPRA));
        List<Agrupada> devCompras = filtrar(todas, DEV_COMPRAS);
        List<Agrupada> invFinal = filtrar(todas, INV_FINAL);
        boolean hayCompras = !compras.isEmpty() || !devCompras.isEmpty();

        if (invInicial.isEmpty() && !hayCompras && invFinal.isEmpty()) {
            b.fila("Costo de lo vendido", RESULTADO, 0, null, m(3, 0, SIMPLE));
        } else {
            if (!invInicial.isEmpty()) {
                b.fila("Inventario inicial", DETALLE, 0, comentarios(invInicial), m(2, t.invInicial, null));
            }
            if (hayCompras) {
                String rayaComprasNetas = invInicial.isEmpty() ? null : SIMPLE;   // se suma al inventario inicial
                if (devCompras.isEmpty()) {
                    if (compras.size() == 1) {
                        Agrupada a = compras.get(0);
                        b.fila(a.nombre, DETALLE, 1, a.comentario(), m(2, t.comprasNetas, rayaComprasNetas));
                    } else {
                        detalle(b, compras, 1, 1, -1, null, null, null);
                        b.fila("Compras netas", DETALLE, 0, null, m(2, t.comprasNetas, rayaComprasNetas));
                    }
                } else {
                    if (compras.size() == 1) {
                        Agrupada a = compras.get(0);
                        b.fila(a.nombre, DETALLE, 1, a.comentario(), m(1, t.comprasTotales, null));
                    } else if (compras.size() > 1) {
                        detalle(b, compras, 1, 0, -1, null, null, null);
                        b.fila("Compras totales", DETALLE, 1, null, m(1, t.comprasTotales, null));
                    }
                    detalle(b, devCompras, 1, 0, 1, SIMPLE, null, null);
                    b.fila("Compras netas", DETALLE, 0, null, m(2, t.comprasNetas, rayaComprasNetas));
                }
            }
            if (!invInicial.isEmpty() && hayCompras) {
                b.fila("Suma o total de mercancías", DETALLE, 0, null, m(2, t.totalMercancias, null));
            }
            if (!invFinal.isEmpty()) {
                b.fila("Inventario final", DETALLE, 0, comentarios(invFinal), m(2, t.invFinal, SIMPLE));
            }
            b.fila("Costo de lo vendido", RESULTADO, 0, null, m(3, t.costoVendido, SIMPLE));
        }
        b.fila(etiqueta(t.utilidadBruta, "Utilidad bruta", "Pérdida bruta"),
                RESULTADO, 0, null, m(3, t.utilidadBruta, null));

        // ---------------- Gastos de operación
        List<Agrupada> gv = filtrar(todas, GASTOS_VENTA);
        List<Agrupada> ga = filtrar(todas, GASTOS_ADMIN);
        List<Agrupada> pf = filtrar(todas, PROD_FINANCIEROS);
        List<Agrupada> gf = filtrar(todas, GASTOS_FINANCIEROS);
        boolean hayGastos = !gv.isEmpty() || !ga.isEmpty();
        boolean hayFinancieros = !pf.isEmpty() || !gf.isEmpty();

        if (!hayGastos && !hayFinancieros) {
            b.fila("Gastos de operación", RESULTADO, 0, null, m(3, 0, SIMPLE));
        } else {
            b.titulo("Gastos de operación", GRUPO, 0);
            if (hayGastos) {
                par(b, new String[]{"Gastos de venta", "Gastos de administración"}, List.of(gv, ga),
                        new int[]{1, 1}, hayFinancieros ? t.gastosVentaAdmin : null, null);
            }
            if (hayFinancieros) {
                // productos financieros - gastos financieros (resta a los gastos de operación)
                par(b, new String[]{"Productos financieros", "Gastos financieros"}, List.of(pf, gf),
                        new int[]{1, -1}, hayGastos ? t.resultadoFinanciero : null, SIMPLE);
            }
            b.fila("Total de gastos de operación", DETALLE, 0, null, m(3, t.gastosOperacion, SIMPLE));
        }
        b.fila(etiqueta(t.utilidadOperacion, "Utilidad de operación", "Pérdida de operación"),
                RESULTADO, 0, null, m(3, t.utilidadOperacion, null));

        // ---------------- Otros gastos y otros productos
        List<Agrupada> og = filtrar(todas, OTROS_GASTOS);
        List<Agrupada> op = filtrar(todas, OTROS_PRODUCTOS);
        if (!og.isEmpty() && !op.isEmpty()) {
            b.titulo("Otros gastos", GRUPO, 0);
            detalle(b, og, 1, 1, 2, null, null, null);
            b.titulo("Otros productos", GRUPO, 0);
            detalle(b, op, 1, 1, 2, SIMPLE, null, null);
            b.fila(t.otrosNeto >= 0 ? "Utilidad neta entre otros gastos y productos"
                            : "Pérdida neta entre otros gastos y productos",
                    DETALLE, 0, null, m(3, Math.abs(t.otrosNeto), SIMPLE));
        } else if (!og.isEmpty()) {
            b.titulo("Otros gastos", GRUPO, 0);
            detalle(b, og, 1, 2, 3, SIMPLE, null, null);
        } else if (!op.isEmpty()) {
            b.titulo("Otros productos", GRUPO, 0);
            detalle(b, op, 1, 2, 3, SIMPLE, null, null);
        }
        b.fila(etiqueta(t.utilidadAntesImpuestos, "Utilidad antes de ISR y PTU", "Pérdida antes de ISR y PTU"),
                RESULTADO, 0, null, m(3, t.utilidadAntesImpuestos, null));

        // ---------------- ISR y PTU (calculados con las tasas)
        b.fila("Impuesto sobre la renta (ISR) " + ReporteFinanciero.porcentaje(tasaIsr) + " %",
                DETALLE, 1, null, m(2, t.isr, null));
        b.fila("Participación de los trabajadores en las utilidades (PTU) " + ReporteFinanciero.porcentaje(tasaPtu) + " %",
                DETALLE, 1, null, m(2, t.ptu, SIMPLE), m(3, t.isr + t.ptu, SIMPLE));

        b.fila(etiqueta(t.utilidadNeta, "Utilidad neta del ejercicio", "Pérdida neta del ejercicio"),
                TOTAL, 0, null, m(3, t.utilidadNeta, DOBLE));
        return b.estado;
    }

    /**
     * Par de grupos que se combinan (gastos de venta + administración, productos - gastos financieros).
     * Cada grupo: sus cuentas en la col 0 y su suma en la col 1; el último grupo lleva además
     * el total del par en la col 2 (si se pide). Con un solo grupo se recorre una columna a la derecha.
     */
    private void par(Constructor b, String[] titulos, List<List<Agrupada>> grupos, int[] signos,
                     Double totalPar, String rayaPar) {
        List<Integer> conDatos = new ArrayList<>();
        for (int i = 0; i < grupos.size(); i++) {
            if (!grupos.get(i).isEmpty()) conDatos.add(i);
        }
        for (int k = 0; k < conDatos.size(); k++) {
            int i = conDatos.get(k);
            boolean ultimo = k == conDatos.size() - 1;
            b.titulo(titulos[i], GRUPO, 1);
            if (conDatos.size() == 1 && totalPar != null) {
                detalle(b, grupos.get(i), 2, 1, 2, rayaPar, totalPar, null);
            } else {
                String rayaGrupo = (ultimo && conDatos.size() > 1) ? SIMPLE : null;
                Monto extra = (ultimo && totalPar != null) ? m(2, totalPar, rayaPar) : null;
                detalle(b, grupos.get(i), 2, 0, 1, rayaGrupo, null, extra);
            }
        }
    }

    /**
     * Escalonado clásico: cada cuenta en la columna de detalle y, en el renglón de la última,
     * la suma en la columna siguiente. Si sólo hay una cuenta, va directo a la columna de total.
     * colTotal = -1 -> sólo detalle (la suma se pone después en su propio renglón).
     */
    private void detalle(Constructor b, List<Agrupada> cuentas, int sangria, int colDetalle, int colTotal,
                         String rayaTotal, Double totalForzado, Monto extra) {
        if (cuentas.isEmpty()) return;
        double total = totalForzado != null ? totalForzado : suma(cuentas);
        if (cuentas.size() == 1 && colTotal >= 0) {
            Agrupada a = cuentas.get(0);
            b.fila(a.nombre, DETALLE, sangria, a.comentario(), m(colTotal, total, rayaTotal), extra);
            return;
        }
        for (int i = 0; i < cuentas.size(); i++) {
            Agrupada a = cuentas.get(i);
            boolean ultima = i == cuentas.size() - 1;
            if (!ultima) {
                b.fila(a.nombre, DETALLE, sangria, a.comentario(), m(colDetalle, a.importe, null));
            } else if (colTotal < 0) {
                b.fila(a.nombre, DETALLE, sangria, a.comentario(), m(colDetalle, a.importe, SIMPLE));
            } else {
                b.fila(a.nombre, DETALLE, sangria, a.comentario(),
                        m(colDetalle, a.importe, SIMPLE), m(colTotal, total, rayaTotal), extra);
            }
        }
    }

    // ------------------------------------------------------------ Utilidades

    private static String etiqueta(double valor, String siPositivo, String siNegativo) {
        return valor < -0.004 ? siNegativo : siPositivo;
    }

    private static Monto m(int columna, double valor, String raya) {
        return new Monto(columna, valor, raya);
    }

    private static double suma(List<Agrupada> cuentas) {
        return cuentas.stream().mapToDouble(a -> a.importe).sum();
    }

    private static String comentarios(List<Agrupada> cuentas) {
        Set<String> set = new LinkedHashSet<>();
        for (Agrupada a : cuentas) {
            String c = a.comentario();
            if (c != null) set.add(c);
        }
        return set.isEmpty() ? null : String.join("; ", set);
    }

    private static List<Agrupada> filtrar(List<Agrupada> cuentas, String rubro) {
        List<Agrupada> res = new ArrayList<>();
        for (Agrupada a : cuentas) {
            if (a.rubro.equals(rubro)) res.add(a);
        }
        return res;
    }

    /** Suma movimientos repetidos de la misma cuenta (p. ej. dos registros de "Compras"). */
    private List<Agrupada> agrupar(List<Cuenta> cuentas) {
        Map<String, Agrupada> mapa = new LinkedHashMap<>();
        for (Cuenta c : cuentas) {
            if (c.getNombre() == null || c.getImporte() == null) continue;
            String rubro = normalizarRubro(c.getSubclasificacion());
            if (rubro == null) continue;   // no pertenece al estado de resultados
            String nombre = c.getNombre().trim();
            Agrupada a = mapa.computeIfAbsent(rubro + "|" + nombre.toLowerCase(), k -> new Agrupada(nombre, rubro));
            a.importe += c.getImporte();
            if (c.getComentarios() != null && !c.getComentarios().isBlank()) {
                a.comentarios.add(c.getComentarios().trim());
            }
        }
        return new ArrayList<>(mapa.values());
    }

    private static String sinAcentos(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase();
    }

    /** Devuelve el rubro oficial (acepta mayúsculas/acentos distintos y algunos nombres viejos) o null. */
    private static String normalizarRubro(String sub) {
        if (sub == null) return null;
        String s = sinAcentos(sub);
        for (List<String> rubros : RUBROS.values()) {
            for (String r : rubros) {
                if (sinAcentos(r).equals(s)) return r;
            }
        }
        if (s.equals("devoluciones") || s.equals("devoluciones sobre ventas")) return DEV_VENTAS;
        if (s.equals("devoluciones sobre compras")) return DEV_COMPRAS;
        return null;
    }

    private static String seccionDe(String rubro) {
        for (Map.Entry<String, List<String>> e : RUBROS.entrySet()) {
            if (e.getValue().contains(rubro)) return e.getKey();
        }
        return null;
    }

    // --------------------------------------------------------- Tipos internos

    private record Monto(int columna, double valor, String raya) {}

    private static final class Agrupada {
        final String nombre;
        final String rubro;
        double importe;
        final Set<String> comentarios = new LinkedHashSet<>();

        Agrupada(String nombre, String rubro) {
            this.nombre = nombre;
            this.rubro = rubro;
        }

        String comentario() {
            return comentarios.isEmpty() ? null : String.join("; ", comentarios);
        }
    }

    private static final class Totales {
        final double ventas, devVentas, ventasNetas;
        final double invInicial, comprasTotales, devCompras, comprasNetas, totalMercancias, invFinal, costoVendido;
        final double utilidadBruta;
        final double gastosVentaAdmin, resultadoFinanciero, gastosOperacion, utilidadOperacion;
        final double otrosNeto, utilidadAntesImpuestos, isr, ptu, utilidadNeta;

        Totales(List<Agrupada> c, double tasaIsr, double tasaPtu) {
            ventas = suma(filtrar(c, VENTAS));
            devVentas = suma(filtrar(c, DEV_VENTAS));
            ventasNetas = ventas - devVentas;

            invInicial = suma(filtrar(c, INV_INICIAL));
            comprasTotales = suma(filtrar(c, COMPRAS)) + suma(filtrar(c, GASTOS_COMPRA));
            devCompras = suma(filtrar(c, DEV_COMPRAS));
            comprasNetas = comprasTotales - devCompras;
            totalMercancias = invInicial + comprasNetas;
            invFinal = suma(filtrar(c, INV_FINAL));
            costoVendido = totalMercancias - invFinal;
            utilidadBruta = ventasNetas - costoVendido;

            gastosVentaAdmin = suma(filtrar(c, GASTOS_VENTA)) + suma(filtrar(c, GASTOS_ADMIN));
            resultadoFinanciero = suma(filtrar(c, PROD_FINANCIEROS)) - suma(filtrar(c, GASTOS_FINANCIEROS));
            gastosOperacion = gastosVentaAdmin - resultadoFinanciero;
            utilidadOperacion = utilidadBruta - gastosOperacion;

            otrosNeto = suma(filtrar(c, OTROS_PRODUCTOS)) - suma(filtrar(c, OTROS_GASTOS));
            utilidadAntesImpuestos = utilidadOperacion + otrosNeto;
            double base = Math.max(0, utilidadAntesImpuestos);
            isr = base * tasaIsr;
            ptu = base * tasaPtu;
            utilidadNeta = utilidadAntesImpuestos - isr - ptu;
        }
    }

    /**
     * Arma las líneas y coloca el signo de pesos a la manera clásica:
     * en la primera cifra de cada columna y en la primera cifra después de una raya.
     */
    private static final class Constructor {
        final EstadoFinanciero estado;
        final boolean[] pesosPendiente;

        Constructor(String tipo, String titulo, int columnas) {
            estado = new EstadoFinanciero(tipo, titulo, columnas);
            pesosPendiente = new boolean[columnas];
            Arrays.fill(pesosPendiente, true);
        }

        void titulo(String concepto, String estilo, int sangria) {
            estado.getLineas().add(new Linea(concepto, estilo, sangria,
                    new Celda[estado.getNumColumnas()], null));
        }

        void fila(String concepto, String estilo, int sangria, String comentario, Monto... montos) {
            Celda[] celdas = new Celda[estado.getNumColumnas()];
            for (Monto mo : montos) {
                if (mo == null) continue;
                int col = mo.columna();
                boolean pesos = pesosPendiente[col] || DOBLE.equals(mo.raya());
                pesosPendiente[col] = mo.raya() != null;
                celdas[col] = new Celda(mo.valor(), pesos, mo.raya());
            }
            estado.getLineas().add(new Linea(concepto, estilo, sangria, celdas, comentario));
        }
    }
}