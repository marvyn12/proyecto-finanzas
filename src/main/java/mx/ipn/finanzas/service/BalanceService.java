package mx.ipn.finanzas.service;

import mx.ipn.finanzas.dto.BalanceGeneral;
import mx.ipn.finanzas.dto.Celda;
import mx.ipn.finanzas.dto.Comparativo;
import mx.ipn.finanzas.dto.Formato;
import mx.ipn.finanzas.dto.Linea;
import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.CuentaCatalogo;
import mx.ipn.finanzas.model.Empresa;
import mx.ipn.finanzas.model.Saldo;
import mx.ipn.finanzas.model.Seccion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hace las cuentas del balance general (módulos 3, 4 y 5). No toca la base de datos: recibe las cuentas
 * ya leídas y regresa el reporte armado. Por eso se puede probar con JUnit directo (BalanceServiceTest).
 *
 * Formato clásico en 3 columnas escalonadas:
 *   columna 1: el importe de cada cuenta
 *   columna 2: la suma de cada clasificación (Total activo circulante…)
 *   columna 3: el total de cada sección (Total activo, Total pasivo, Total capital contable)
 * El signo $ va en la primera cifra de cada columna y en los totales; raya simple antes de cada suma
 * y raya doble bajo "Total activo" y "Total pasivo y capital contable".
 */
@Service
public class BalanceService {

    private static final int COLUMNAS = 3;
    private static final String SIMPLE = "simple";
    private static final String DOBLE = "doble";

    // ================================================================== Balance general

    /** Balance estático: las cuentas registradas a una fecha ("Al 31 de diciembre de 2026"). */
    public BalanceGeneral generar(Empresa empresa, List<Saldo> saldos, List<CuentaCatalogo> catalogo,
                                  LocalDate fecha, LocalDate hoy) {
        return generar(empresa, saldos, catalogo, null, fecha, hoy);
    }

    /**
     * inicio == null → estático (a la fecha "fin").
     * inicio != null → dinámico: suma todo lo registrado del inicio al fin ("Del 1 de enero al 31 de diciembre de 2026");
     *                  si una cuenta se registró en varias fechas del periodo, sus importes se suman.
     */
    public BalanceGeneral generar(Empresa empresa, List<Saldo> registros, List<CuentaCatalogo> catalogo,
                                  LocalDate inicio, LocalDate fin, LocalDate hoy) {
        boolean dinamico = inicio != null;
        List<Saldo> saldos = dinamico ? consolidar(registros) : registros;
        BalanceGeneral b = new BalanceGeneral();
        b.setEmpresa(empresa.getNombre());
        b.setDinamico(dinamico);
        b.setInicio(dinamico ? inicio.toString() : null);
        b.setFecha(fin.toString());
        b.setFechaTexto(dinamico ? Fechas.periodo(inicio, fin) : Fechas.al(fin));
        b.setElaborado(Fechas.larga(hoy));
        b.setNumCuentas(saldos.size());
        b.setRegistros(registros.size());
        b.setNumFechas((int) registros.stream().map(Saldo::getFecha).distinct().count());

        Map<Clasificacion, List<Saldo>> grupos = agrupar(saldos, empresa.getOrdenCuentas(), catalogo);

        // 1. Sumas por clasificación y por sección (aunque haya ceros: los ceros no cambian la suma)
        Map<Clasificacion, BigDecimal> sumaClasif = new EnumMap<>(Clasificacion.class);
        Map<Seccion, BigDecimal> sumaSeccion = new EnumMap<>(Seccion.class);
        for (Seccion s : Seccion.values()) sumaSeccion.put(s, BigDecimal.ZERO);
        for (Clasificacion c : Clasificacion.values()) {
            BigDecimal t = BigDecimal.ZERO;
            for (Saldo s : grupos.get(c)) t = t.add(s.getValor());
            sumaClasif.put(c, t);
            sumaSeccion.merge(c.getSeccion(), t, BigDecimal::add);
        }
        b.setTotalActivo(Formato.redondear(sumaSeccion.get(Seccion.ACTIVO)));
        b.setTotalPasivo(Formato.redondear(sumaSeccion.get(Seccion.PASIVO)));
        b.setTotalCapital(Formato.redondear(sumaSeccion.get(Seccion.CAPITAL)));
        b.setActivoCirculante(Formato.redondear(sumaClasif.get(Clasificacion.ACTIVO_CIRCULANTE)));
        b.setPasivoCorto(Formato.redondear(sumaClasif.get(Clasificacion.PASIVO_CORTO)));

        for (Seccion s : Seccion.values()) {
            for (Clasificacion c : Clasificacion.de(s)) {
                int visibles = (int) grupos.get(c).stream().filter(x -> !x.isEnCero()).count();
                b.getSumas().add(new BalanceGeneral.Suma(s.getNombre(), c.getNombre(), c.getPlazo(),
                        sumaClasif.get(c), visibles, false));
            }
            b.getSumas().add(new BalanceGeneral.Suma(s.getNombre(), s.getTotal(), null, sumaSeccion.get(s), 0, true));
        }

        // 2. Cuentas en $0: se guardan pero no se muestran
        for (Clasificacion c : Clasificacion.values()) {
            for (Saldo s : grupos.get(c)) if (s.isEnCero()) b.getOmitidas().add(s.getNombre());
        }

        // 3. Líneas del reporte
        boolean hayAlgo = saldos.stream().anyMatch(s -> !s.isEnCero());
        if (hayAlgo) {
            boolean pasivoVisible = false;
            for (Seccion sec : Seccion.values()) {
                List<Clasificacion> visibles = new ArrayList<>();
                for (Clasificacion c : Clasificacion.de(sec)) {
                    if (grupos.get(c).stream().anyMatch(x -> !x.isEnCero())) visibles.add(c);
                }
                if (visibles.isEmpty()) continue;
                if (sec == Seccion.PASIVO) pasivoVisible = true;

                b.getLineas().add(new Linea(sec.getNombre(), "seccion", 0, vacias(), null));
                for (int i = 0; i < visibles.size(); i++) {
                    Clasificacion c = visibles.get(i);
                    b.getLineas().add(new Linea(c.getEncabezado(), "grupo", 1, vacias(), null));
                    List<Saldo> cuentas = grupos.get(c).stream().filter(x -> !x.isEnCero()).toList();
                    for (int j = 0; j < cuentas.size(); j++) {
                        Saldo s = cuentas.get(j);
                        boolean ultima = j == cuentas.size() - 1;
                        b.getLineas().add(new Linea(s.getNombre(), "detalle", 2,
                                celda(0, s.getValor(), j == 0, ultima ? SIMPLE : null), s.getComentario()));
                    }
                    boolean ultimaClasif = i == visibles.size() - 1;
                    b.getLineas().add(new Linea(c.getTotal(), "subtotal", 1,
                            celda(1, sumaClasif.get(c), i == 0, ultimaClasif ? SIMPLE : null), null));
                }
                BigDecimal total = sumaSeccion.get(sec);
                Celda[] celdas = switch (sec) {
                    case ACTIVO -> celda(2, total, true, DOBLE);
                    case PASIVO -> celda(2, total, true, null);
                    case CAPITAL -> celda(2, total, !pasivoVisible, SIMPLE);
                };
                b.getLineas().add(new Linea(sec.getTotal(), "total", 0, celdas, null));
            }
            b.getLineas().add(new Linea("Total pasivo y capital contable", "total", 0,
                    celda(2, b.getTotalPasivoCapital(), true, DOBLE), null));
        }

        // 4. Validaciones
        advertencias(b, grupos, sumaClasif);
        if (hayAlgo && !b.isCuadra()) pistas(b, saldos);
        return b;
    }

    // ================================================================== Comparativo

    /** Comparativo estático: dos fechas. */
    public Comparativo comparar(Empresa empresa, List<CuentaCatalogo> catalogo,
                                List<Saldo> actuales, LocalDate fechaActual,
                                List<Saldo> anteriores, LocalDate fechaAnterior) {
        return comparar(empresa, catalogo, actuales, null, fechaActual, anteriores, null, fechaAnterior);
    }

    /** Comparativo dinámico (si vienen los inicios): dos periodos. Las cuentas repetidas en un periodo se suman. */
    public Comparativo comparar(Empresa empresa, List<CuentaCatalogo> catalogo,
                                List<Saldo> actuales, LocalDate inicioActual, LocalDate fechaActual,
                                List<Saldo> anteriores, LocalDate inicioAnterior, LocalDate fechaAnterior) {
        boolean dinamico = inicioActual != null && inicioAnterior != null;
        Comparativo comp = new Comparativo();
        comp.setEmpresa(empresa.getNombre());
        String[] etiquetas = dinamico ? Fechas.etiquetas(inicioActual, fechaActual, inicioAnterior, fechaAnterior)
                : Fechas.etiquetas(fechaActual, fechaAnterior);
        comp.setEtiquetaActual(etiquetas[0]);
        comp.setEtiquetaAnterior(etiquetas[1]);
        comp.setFechaActual(fechaActual.toString());
        comp.setFechaAnterior(fechaAnterior.toString());
        comp.setFechaActualTexto(dinamico ? Fechas.periodo(inicioActual, fechaActual) : Fechas.al(fechaActual));
        comp.setFechaAnteriorTexto(dinamico ? Fechas.periodo(inicioAnterior, fechaAnterior) : Fechas.al(fechaAnterior));

        // Unión de cuentas de las dos fechas, en el orden de la empresa
        Map<String, BigDecimal[]> valores = new LinkedHashMap<>();
        List<Saldo> union = new ArrayList<>();
        for (int k = 0; k < 2; k++) {
            for (Saldo s : k == 0 ? actuales : anteriores) {
                String llave = s.getClasificacion() + "|" + Clasificador.normalizar(s.getNombre());
                BigDecimal[] v = valores.get(llave);
                if (v == null) {
                    v = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                    valores.put(llave, v);
                    union.add(s);
                }
                v[k] = v[k].add(s.getValor());
            }
        }
        Map<Clasificacion, List<Saldo>> grupos = agrupar(union, empresa.getOrdenCuentas(), catalogo);

        BigDecimal[] activo = {BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] pasivoCapital = {BigDecimal.ZERO, BigDecimal.ZERO};
        boolean hayAlgo = false;
        for (Seccion sec : Seccion.values()) {
            BigDecimal[] totalSec = {BigDecimal.ZERO, BigDecimal.ZERO};
            boolean seccionVisible = false;
            boolean primera = true;
            List<Comparativo.Fila> filasSeccion = new ArrayList<>();
            for (Clasificacion c : Clasificacion.de(sec)) {
                BigDecimal[] totalClasif = {BigDecimal.ZERO, BigDecimal.ZERO};
                List<Saldo> visibles = new ArrayList<>();
                for (Saldo s : grupos.get(c)) {
                    BigDecimal[] v = valores.get(s.getClasificacion() + "|" + Clasificador.normalizar(s.getNombre()));
                    totalClasif[0] = totalClasif[0].add(v[0]);
                    totalClasif[1] = totalClasif[1].add(v[1]);
                    if (v[0].signum() != 0 || v[1].signum() != 0) visibles.add(s);
                }
                totalSec[0] = totalSec[0].add(totalClasif[0]);
                totalSec[1] = totalSec[1].add(totalClasif[1]);
                if (visibles.isEmpty()) continue;
                seccionVisible = true;
                filasSeccion.add(new Comparativo.Fila(c.getEncabezado(), "grupo", 1, null, null, null, null));
                for (int j = 0; j < visibles.size(); j++) {
                    Saldo s = visibles.get(j);
                    BigDecimal[] v = valores.get(s.getClasificacion() + "|" + Clasificador.normalizar(s.getNombre()));
                    filasSeccion.add(fila(s.getNombre(), "detalle", 2, v[0], v[1], primera,
                            j == visibles.size() - 1 ? SIMPLE : null));
                    primera = false;
                }
                filasSeccion.add(fila(c.getTotal(), "subtotal", 1, totalClasif[0], totalClasif[1], false, null));
            }
            if (sec == Seccion.ACTIVO) activo = totalSec;
            else {
                pasivoCapital[0] = pasivoCapital[0].add(totalSec[0]);
                pasivoCapital[1] = pasivoCapital[1].add(totalSec[1]);
            }
            if (!seccionVisible) continue;
            hayAlgo = true;
            comp.getFilas().add(new Comparativo.Fila(sec.getNombre(), "seccion", 0, null, null, null, null));
            comp.getFilas().addAll(filasSeccion);
            String raya = sec == Seccion.ACTIVO ? DOBLE : (sec == Seccion.CAPITAL ? SIMPLE : null);
            comp.getFilas().add(fila(sec.getTotal(), "total", 0, totalSec[0], totalSec[1], true, raya));
        }
        if (hayAlgo) {
            comp.getFilas().add(fila("Total pasivo y capital contable", "total", 0,
                    pasivoCapital[0], pasivoCapital[1], true, DOBLE));
        }
        BigDecimal dif0 = Formato.redondear(activo[0].subtract(pasivoCapital[0]));
        BigDecimal dif1 = Formato.redondear(activo[1].subtract(pasivoCapital[1]));
        comp.setCuadraActual(dif0.signum() == 0);
        comp.setCuadraAnterior(dif1.signum() == 0);
        comp.setDiferenciaActual(Formato.pesos(dif0.abs()));
        comp.setDiferenciaAnterior(Formato.pesos(dif1.abs()));
        return comp;
    }

    private static Comparativo.Fila fila(String concepto, String estilo, int sangria, BigDecimal a, BigDecimal b,
                                         boolean pesos, String raya) {
        BigDecimal variacion = a.subtract(b);
        return new Comparativo.Fila(concepto, estilo, sangria,
                new Celda(a, pesos, raya), new Celda(b, pesos, raya), new Celda(variacion, pesos, raya),
                Formato.variacion(a, b));
    }

    // ================================================================== Balance dinámico

    /**
     * Junta en una sola línea las capturas de la misma cuenta (misma clasificación, nombre y signo) sumando sus importes.
     * Regresa copias: no modifica lo que viene de la base de datos.
     */
    public static List<Saldo> consolidar(List<Saldo> registros) {
        Map<String, Saldo> m = new LinkedHashMap<>();
        for (Saldo s : registros) {
            String llave = s.getClasificacion() + "|" + Clasificador.normalizar(s.getNombre()) + "|" + s.isResta();
            Saldo t = m.get(llave);
            if (t == null) {
                t = new Saldo();
                t.setId(s.getId());
                t.setEmpresa(s.getEmpresa());
                t.setFecha(s.getFecha());
                t.setNombre(s.getNombre());
                t.setClasificacion(s.getClasificacion());
                t.setResta(s.isResta());
                t.setImporte(s.getImporte());
                t.setComentario(s.getComentario());
                m.put(llave, t);
                continue;
            }
            t.setImporte(t.getImporte().add(s.getImporte()));
            if (s.getFecha().isAfter(t.getFecha())) t.setFecha(s.getFecha());
            if (t.getId() == null || (s.getId() != null && s.getId() < t.getId())) t.setId(s.getId());
            if (s.getComentario() != null && (t.getComentario() == null || !t.getComentario().contains(s.getComentario()))) {
                String c = t.getComentario() == null ? s.getComentario() : t.getComentario() + "; " + s.getComentario();
                t.setComentario(c.length() > 255 ? c.substring(0, 254) + "…" : c);
            }
        }
        return new ArrayList<>(m.values());
    }

    // ================================================================== Orden de las cuentas

    /**
     * Agrupa por clasificación y ordena dentro de cada una según la empresa:
     * CATALOGO (orden del catálogo: liquidez/exigibilidad), ALFABETICO o CAPTURA.
     * Después pega cada complementaria debajo de la cuenta a la que resta.
     */
    public Map<Clasificacion, List<Saldo>> agrupar(List<Saldo> saldos, String orden, List<CuentaCatalogo> catalogo) {
        Map<Clasificacion, List<Saldo>> m = new EnumMap<>(Clasificacion.class);
        for (Clasificacion c : Clasificacion.values()) m.put(c, new ArrayList<>());
        for (Saldo s : saldos) m.get(s.getClasificacion()).add(s);

        Comparator<Saldo> porCaptura = Comparator.comparing(Saldo::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Saldo> cmp;
        if (Empresa.ORDEN_ALFABETICO.equals(orden)) {
            Collator col = Collator.getInstance(Locale.forLanguageTag("es-MX"));
            col.setStrength(Collator.PRIMARY);
            cmp = Comparator.<Saldo, String>comparing(Saldo::getNombre, col).thenComparing(porCaptura);
        } else if (Empresa.ORDEN_CAPTURA.equals(orden)) {
            cmp = porCaptura;
        } else {
            Map<String, Integer> posicion = new HashMap<>();
            if (catalogo != null) {
                for (CuentaCatalogo c : catalogo) {
                    posicion.putIfAbsent(c.getClasificacion() + "|" + Clasificador.normalizar(c.getNombre()), c.getOrden());
                }
            }
            cmp = Comparator.<Saldo>comparingInt(s -> posicion.getOrDefault(
                    s.getClasificacion() + "|" + Clasificador.normalizar(s.getNombre()), Integer.MAX_VALUE))
                    .thenComparing(porCaptura);
        }
        for (List<Saldo> l : m.values()) {
            l.sort(cmp);
            pegarComplementarias(l);
        }
        return m;
    }

    /** Mueve cada complementaria (que resta) justo debajo de su cuenta principal, si está en la misma lista. */
    private static void pegarComplementarias(List<Saldo> l) {
        for (Saldo comp : new ArrayList<>(l)) {
            if (!comp.isResta()) continue;
            Saldo principal = principalDe(comp, l);
            if (principal == null) continue;
            l.remove(comp);
            int i = l.indexOf(principal) + 1;
            while (i < l.size() && l.get(i).isResta() && principal == principalDe(l.get(i), l)) i++;
            l.add(i, comp);
        }
    }

    private static final Pattern DE_QUE = Pattern.compile("^(depreciacion|amortizacion)(es)? acumuladas? (de |del |de la |de los |de las )?(.+)$");

    /**
     * La cuenta a la que resta una complementaria: "Depreciación acumulada de edificios" → "Edificios";
     * "Estimación para cuentas incobrables" → "Clientes". null si no se sabe o no está.
     */
    static Saldo principalDe(Saldo complementaria, List<Saldo> candidatas) {
        String buscado = nombrePrincipal(complementaria.getNombre());
        if (buscado == null) return null;
        for (Saldo s : candidatas) {
            if (s == complementaria || s.isResta()) continue;
            String n = raiz(Clasificador.normalizar(s.getNombre()));
            if (n.equals(buscado) || n.startsWith(buscado + " ") || buscado.startsWith(n + " ")) return s;
        }
        return null;
    }

    /** Nombre (normalizado y sin plurales) de la cuenta principal de una complementaria, o null. */
    static String nombrePrincipal(String complementaria) {
        String n = Clasificador.normalizar(complementaria);
        if (n.contains("incobrable") || n.contains("cobro dudoso")) return "cliente";
        Matcher m = DE_QUE.matcher(n);
        return m.matches() ? raiz(m.group(4)) : null;
    }

    /** Quita la "s" final de cada palabra para comparar singular con plural: "edificios" → "edificio". */
    private static String raiz(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.length() > 3 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
        }
        return sb.toString();
    }

    // ================================================================== Validaciones (avisos)

    private static void advertencias(BalanceGeneral b, Map<Clasificacion, List<Saldo>> grupos,
                                     Map<Clasificacion, BigDecimal> sumaClasif) {
        List<String> a = b.getAdvertencias();
        for (Clasificacion c : Clasificacion.values()) {
            List<Saldo> l = grupos.get(c);
            for (Saldo s : l) {
                if (s.isEnCero()) continue;
                // ¿Está bien clasificada según su nombre?
                Clasificador.sugerir(s.getNombre()).ifPresent(sug -> {
                    if (sug.clasificacion() != s.getClasificacion()) {
                        a.add("«" + s.getNombre() + "» está en " + s.getClasificacion().getNombre()
                                + ", pero por su nombre parece " + sug.clasificacion().getNombre() + ". " + sug.motivo());
                    } else if (sug.resta() && !s.isResta()) {
                        a.add("«" + s.getNombre() + "» está sumando, pero es una cuenta complementaria: debería restar.");
                    }
                });
                // Complementaria contra su cuenta principal
                if (s.isResta()) {
                    String buscado = nombrePrincipal(s.getNombre());
                    Saldo principal = principalDe(s, l.stream().filter(x -> !x.isEnCero()).toList());
                    if (buscado != null && principal == null) {
                        a.add("Registraste «" + s.getNombre() + "» pero no la cuenta a la que resta.");
                    } else if (principal != null && s.getImporte().compareTo(principal.getImporte()) > 0) {
                        a.add("«" + s.getNombre() + "» (" + Formato.pesos(s.getImporte()) + ") es mayor que «"
                                + principal.getNombre() + "» (" + Formato.pesos(principal.getImporte())
                                + "): el valor neto no puede quedar negativo.");
                    }
                }
            }
            if (c.getSeccion() != Seccion.CAPITAL && sumaClasif.get(c).signum() < 0) {
                a.add("El total de " + c.getNombre().toLowerCase() + " es negativo ("
                        + Formato.pesos(sumaClasif.get(c)) + "): las complementarias restan más que las cuentas.");
            }
        }
        boolean utilidad = tiene(grupos.get(Clasificacion.CAPITAL_GANADO), "utilidad del ejercicio");
        boolean perdida = tiene(grupos.get(Clasificacion.CAPITAL_GANADO), "perdida del ejercicio");
        if (utilidad && perdida) {
            a.add("Registraste utilidad y pérdida del ejercicio a la vez: en un ejercicio solo hay una de las dos.");
        }
        if (b.isCapitalNegativo()) {
            a.add("El capital contable es negativo (" + b.getTotalCapitalTexto()
                    + "): la empresa debe más de lo que tiene.");
        }
    }

    private static boolean tiene(List<Saldo> l, String nombreNormalizado) {
        return l.stream().anyMatch(s -> !s.isEnCero() && Clasificador.normalizar(s.getNombre()).equals(nombreNormalizado));
    }

    /** Pistas para encontrar por qué no cuadra la ecuación contable. */
    private static void pistas(BalanceGeneral b, List<Saldo> saldos) {
        List<String> p = b.getPistas();
        BigDecimal d = b.getDiferencia();
        BigDecimal abs = d.abs();
        if (d.signum() > 0) {
            p.add("El activo es mayor que el pasivo + capital por " + Formato.pesos(abs)
                    + ": falta registrar pasivo o capital, o sobra activo.");
        } else {
            p.add("El pasivo + capital es mayor que el activo por " + Formato.pesos(abs)
                    + ": falta registrar activo, o sobra pasivo o capital.");
        }
        if (b.getTotalCapital().signum() == 0) {
            p.add("No hay capital contable registrado (capital social, utilidades…).");
        } else if (d.signum() > 0 && saldos.stream().noneMatch(s -> !s.isEnCero() && s.getClasificacion() == Clasificacion.CAPITAL_GANADO
                && Clasificador.normalizar(s.getNombre()).matches("(utilidad|perdida) del ejercicio"))) {
            p.add("¿Falta la utilidad (o pérdida) del ejercicio? Sale del estado de resultados y va en capital ganado.");
        }
        int extra = 0;
        for (Saldo s : saldos) {
            if (s.isEnCero() || extra >= 3) continue;
            if (s.getImporte().compareTo(abs) == 0) {
                p.add("La diferencia es igual al importe de «" + s.getNombre() + "» (" + Formato.pesos(s.getImporte())
                        + "): revisa si esa cuenta sobra, se repitió o está en la sección equivocada.");
                extra++;
            } else if (s.getImporte().multiply(BigDecimal.valueOf(2)).compareTo(abs) == 0) {
                p.add("La diferencia es el doble de «" + s.getNombre() + "» (" + Formato.pesos(s.getImporte())
                        + "): puede estar del lado equivocado de la ecuación o sumando cuando debería restar.");
                extra++;
            }
        }
    }

    // ================================================================== Utilidades

    private static Celda[] vacias() {
        return new Celda[COLUMNAS];
    }

    private static Celda[] celda(int columna, BigDecimal importe, boolean pesos, String raya) {
        Celda[] c = new Celda[COLUMNAS];
        c[columna] = new Celda(importe, pesos, raya);
        return c;
    }
}
