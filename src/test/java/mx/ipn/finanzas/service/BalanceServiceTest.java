package mx.ipn.finanzas.service;

import mx.ipn.finanzas.dto.BalanceGeneral;
import mx.ipn.finanzas.dto.Celda;
import mx.ipn.finanzas.dto.Comparativo;
import mx.ipn.finanzas.dto.Linea;
import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.CuentaCatalogo;
import mx.ipn.finanzas.model.Empresa;
import mx.ipn.finanzas.model.Saldo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static mx.ipn.finanzas.model.Clasificacion.*;
import static org.junit.jupiter.api.Assertions.*;

/** Módulos 3, 4 y 5: cálculo automático, validación de la ecuación contable y armado del reporte. */
class BalanceServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 12, 31);
    private static final LocalDate HOY = LocalDate.of(2026, 11, 8);
    private final BalanceService servicio = new BalanceService();
    private long siguienteId = 1;

    // ------------------------------------------------------------------ datos de prueba

    private Empresa empresa(String orden) {
        Empresa e = new Empresa();
        e.setId(1L);
        e.setNombre("Comercializadora del Valle, S.A. de C.V.");
        e.setOrdenCuentas(orden);
        return e;
    }

    private Saldo saldo(String nombre, Clasificacion c, String importe) {
        return saldo(nombre, c, importe, false);
    }

    private Saldo saldo(String nombre, Clasificacion c, String importe, boolean resta) {
        Saldo s = new Saldo();
        s.setId(siguienteId++);
        s.setFecha(FECHA);
        s.setNombre(nombre);
        s.setClasificacion(c);
        s.setResta(resta);
        s.setImporte(new BigDecimal(importe).setScale(2));
        return s;
    }

    /** Catálogo base como lo tendría una empresa recién creada. */
    private static List<CuentaCatalogo> catalogo() {
        List<CuentaCatalogo> l = new ArrayList<>();
        int orden = 0;
        for (CatalogoBase.Cuenta b : CatalogoBase.CUENTAS) {
            CuentaCatalogo c = new CuentaCatalogo();
            c.setNombre(b.nombre());
            c.setClasificacion(b.clasificacion());
            c.setResta(b.resta());
            c.setOrden(++orden);
            l.add(c);
        }
        return l;
    }

    /**
     * Activo:  circulante 90,000 + no circulante 180,000 = 270,000
     * Pasivo:  corto 40,000 + largo 100,000 = 140,000
     * Capital: contribuido 100,000 + ganado 30,000 = 130,000      →  270,000 = 140,000 + 130,000 ✔
     */
    private List<Saldo> ejemplo() {
        List<Saldo> l = new ArrayList<>();
        l.add(saldo("Caja", ACTIVO_CIRCULANTE, "10000"));
        l.add(saldo("Bancos", ACTIVO_CIRCULANTE, "50000"));
        l.add(saldo("Clientes", ACTIVO_CIRCULANTE, "30000"));
        l.add(saldo("Edificios", ACTIVO_NO_CIRCULANTE, "200000"));
        l.add(saldo("Depreciación acumulada de edificios", ACTIVO_NO_CIRCULANTE, "20000", true));
        l.add(saldo("Proveedores", PASIVO_CORTO, "40000"));
        l.add(saldo("Préstamos bancarios a largo plazo", PASIVO_LARGO, "100000"));
        l.add(saldo("Capital social", CAPITAL_CONTRIBUIDO, "100000"));
        l.add(saldo("Utilidad del ejercicio", CAPITAL_GANADO, "30000"));
        return l;
    }

    private BalanceGeneral generar(List<Saldo> saldos) {
        return servicio.generar(empresa(Empresa.ORDEN_CATALOGO), saldos, catalogo(), FECHA, HOY);
    }

    private static Linea linea(BalanceGeneral b, String concepto) {
        return b.getLineas().stream().filter(l -> l.getConcepto().equals(concepto)).findFirst()
                .orElseThrow(() -> new AssertionError("No está la línea «" + concepto + "»"));
    }

    private static Celda celda(Linea l) {
        for (Celda c : l.getCeldas()) if (c != null) return c;
        throw new AssertionError("La línea «" + l.getConcepto() + "» no tiene importe");
    }

    private static int columna(Linea l) {
        for (int i = 0; i < l.getCeldas().length; i++) if (l.getCeldas()[i] != null) return i;
        return -1;
    }

    private static BigDecimal pesos(String v) {
        return new BigDecimal(v).setScale(2);
    }

    // ------------------------------------------------------------------ módulo 3: cálculo automático

    @Test
    @DisplayName("Suma activo, pasivo y capital (las complementarias restan)")
    void totales() {
        BalanceGeneral b = generar(ejemplo());
        assertEquals(pesos("270000"), b.getTotalActivo());
        assertEquals(pesos("140000"), b.getTotalPasivo());
        assertEquals(pesos("130000"), b.getTotalCapital());
        assertEquals(pesos("270000"), b.getTotalPasivoCapital());
    }

    @Test
    @DisplayName("Suma cada clasificación (subtotales)")
    void subtotales() {
        BalanceGeneral b = generar(ejemplo());
        assertEquals(pesos("90000"), celda(linea(b, "Total activo circulante")).getImporte());
        assertEquals(pesos("180000"), celda(linea(b, "Total activo no circulante")).getImporte());
        assertEquals(pesos("40000"), celda(linea(b, "Total pasivo a corto plazo")).getImporte());
        assertEquals(pesos("100000"), celda(linea(b, "Total pasivo a largo plazo")).getImporte());
        assertEquals(pesos("100000"), celda(linea(b, "Total capital contribuido")).getImporte());
        assertEquals(pesos("30000"), celda(linea(b, "Total capital ganado")).getImporte());
    }

    @Test
    @DisplayName("Los centavos se suman exactos (sin errores de redondeo)")
    void centavos() {
        List<Saldo> l = new ArrayList<>();
        for (int i = 0; i < 10; i++) l.add(saldo("Cuenta " + i, ACTIVO_CIRCULANTE, "0.10"));
        l.add(saldo("Capital social", CAPITAL_CONTRIBUIDO, "1.00"));
        BalanceGeneral b = generar(l);
        assertEquals(pesos("1.00"), b.getTotalActivo());
        assertTrue(b.isCuadra());
    }

    // ------------------------------------------------------------------ módulo 4: validación contable

    @Test
    @DisplayName("Activo = Pasivo + Capital: cuadra y Activo − Pasivo = Capital")
    void cuadra() {
        BalanceGeneral b = generar(ejemplo());
        assertTrue(b.isCuadra());
        assertEquals(0, b.getDiferencia().signum());
        assertEquals(b.getTotalCapital(), b.getCapitalCalculado());
        assertTrue(b.getPistas().isEmpty());
    }

    @Test
    @DisplayName("Si falta la utilidad del ejercicio no cuadra y lo dice")
    void noCuadra() {
        List<Saldo> l = ejemplo();
        l.removeIf(s -> s.getNombre().equals("Utilidad del ejercicio"));
        BalanceGeneral b = generar(l);
        assertFalse(b.isCuadra());
        assertEquals(pesos("30000"), b.getDiferencia());
        assertEquals("$30,000.00", b.getDiferenciaTexto());
        assertTrue(b.getPistas().get(0).contains("activo es mayor"));
        assertTrue(b.getPistas().stream().anyMatch(p -> p.contains("utilidad")), "Debe sugerir la utilidad del ejercicio");
    }

    @Test
    @DisplayName("Si la diferencia es igual a una cuenta, la señala")
    void pistaCuentaExacta() {
        List<Saldo> l = ejemplo();
        l.add(saldo("Equipo de cómputo", ACTIVO_NO_CIRCULANTE, "25000"));   // sobra de un lado
        BalanceGeneral b = generar(l);
        assertFalse(b.isCuadra());
        assertTrue(b.getPistas().stream().anyMatch(p -> p.contains("«Equipo de cómputo»")));
    }

    @Test
    @DisplayName("Cuenta del lado equivocado: la diferencia es el doble y lo señala")
    void pistaDoble() {
        List<Saldo> l = ejemplo();
        l.removeIf(s -> s.getNombre().equals("Proveedores"));
        l.add(saldo("Proveedores", ACTIVO_CIRCULANTE, "40000"));   // la pusieron en activo
        BalanceGeneral b = generar(l);
        assertEquals(pesos("80000"), b.getDiferencia());
        assertTrue(b.getPistas().stream().anyMatch(p -> p.contains("doble de «Proveedores»")));
        assertTrue(b.getAdvertencias().stream().anyMatch(a -> a.contains("«Proveedores» está en Activo circulante")));
    }

    @Test
    @DisplayName("Avisa si una depreciación es mayor que el bien")
    void complementariaMayor() {
        List<Saldo> l = new ArrayList<>();
        l.add(saldo("Edificios", ACTIVO_NO_CIRCULANTE, "50000"));
        l.add(saldo("Depreciación acumulada de edificios", ACTIVO_NO_CIRCULANTE, "60000", true));
        BalanceGeneral b = generar(l);
        assertTrue(b.getAdvertencias().stream().anyMatch(a -> a.contains("es mayor que «Edificios»")));
        assertTrue(b.getAdvertencias().stream().anyMatch(a -> a.contains("negativo")));
    }

    @Test
    @DisplayName("Avisa si hay depreciación sin el bien al que resta")
    void complementariaSola() {
        List<Saldo> l = ejemplo();
        l.removeIf(s -> s.getNombre().equals("Edificios"));
        BalanceGeneral b = generar(l);
        assertTrue(b.getAdvertencias().stream().anyMatch(a -> a.contains("no la cuenta a la que resta")));
    }

    @Test
    @DisplayName("Avisa si el capital contable es negativo")
    void capitalNegativo() {
        List<Saldo> l = new ArrayList<>();
        l.add(saldo("Bancos", ACTIVO_CIRCULANTE, "10000"));
        l.add(saldo("Proveedores", PASIVO_CORTO, "50000"));
        l.add(saldo("Capital social", CAPITAL_CONTRIBUIDO, "10000"));
        l.add(saldo("Pérdida del ejercicio", CAPITAL_GANADO, "50000", true));
        BalanceGeneral b = generar(l);
        assertTrue(b.isCuadra());
        assertEquals(pesos("-40000"), b.getTotalCapital());
        assertTrue(b.getAdvertencias().stream().anyMatch(a -> a.contains("capital contable es negativo")));
    }

    @Test
    @DisplayName("Un balance bien hecho no tiene avisos")
    void sinAvisos() {
        BalanceGeneral b = generar(ejemplo());
        assertTrue(b.getAdvertencias().isEmpty(), "Avisos: " + b.getAdvertencias());
    }

    // ------------------------------------------------------------------ módulo 5: reporte

    @Test
    @DisplayName("No muestra cuentas en $0.00, pero sí las cuenta")
    void omiteCeros() {
        List<Saldo> l = ejemplo();
        l.add(saldo("Fondo fijo de caja chica", ACTIVO_CIRCULANTE, "0"));
        l.add(saldo("Acreedores hipotecarios", PASIVO_LARGO, "0"));
        BalanceGeneral b = generar(l);
        assertTrue(b.getLineas().stream().noneMatch(x -> x.getConcepto().equals("Fondo fijo de caja chica")));
        assertTrue(b.getLineas().stream().noneMatch(x -> x.getConcepto().equals("Acreedores hipotecarios")));
        assertEquals(List.of("Fondo fijo de caja chica", "Acreedores hipotecarios"), b.getOmitidas());
        assertEquals(11, b.getNumCuentas());
        assertEquals(9, b.getNumMostradas());
        // Ninguna celda del reporte trae un cero
        for (Linea x : b.getLineas()) for (Celda c : x.getCeldas()) if (c != null) assertNotEquals0(c);
    }

    private static void assertNotEquals0(Celda c) {
        assertTrue(c.getImporte().signum() != 0, "Hay un importe en cero en el reporte");
    }

    @Test
    @DisplayName("Una clasificación sin importes no sale ni su encabezado")
    void omiteClasificacionVacia() {
        List<Saldo> l = ejemplo();
        l.removeIf(s -> s.getClasificacion() == PASIVO_LARGO);
        l.add(saldo("Acreedores hipotecarios", PASIVO_LARGO, "0"));
        BalanceGeneral b = generar(l);
        assertTrue(b.getLineas().stream().noneMatch(x -> x.getConcepto().contains("largo plazo")));
    }

    @Test
    @DisplayName("Jerarquía: sección › clasificación › cuentas › subtotal › total")
    void jerarquia() {
        BalanceGeneral b = generar(ejemplo());
        List<String> esperado = List.of(
                "seccion:Activo", "grupo:Circulante", "detalle:Caja", "detalle:Bancos", "detalle:Clientes",
                "subtotal:Total activo circulante", "grupo:No circulante", "detalle:Edificios",
                "detalle:Depreciación acumulada de edificios", "subtotal:Total activo no circulante", "total:Total activo",
                "seccion:Pasivo", "grupo:A corto plazo (circulante)", "detalle:Proveedores", "subtotal:Total pasivo a corto plazo",
                "grupo:A largo plazo (no circulante)", "detalle:Préstamos bancarios a largo plazo",
                "subtotal:Total pasivo a largo plazo", "total:Total pasivo",
                "seccion:Capital contable", "grupo:Contribuido", "detalle:Capital social", "subtotal:Total capital contribuido",
                "grupo:Ganado", "detalle:Utilidad del ejercicio", "subtotal:Total capital ganado", "total:Total capital contable",
                "total:Total pasivo y capital contable");
        List<String> real = b.getLineas().stream().map(x -> x.getEstilo() + ":" + x.getConcepto()).toList();
        assertEquals(esperado, real);
    }

    @Test
    @DisplayName("Formato escalonado: cuentas en col. 1, subtotales en col. 2, totales en col. 3")
    void columnas() {
        BalanceGeneral b = generar(ejemplo());
        assertEquals(0, columna(linea(b, "Caja")));
        assertEquals(1, columna(linea(b, "Total activo circulante")));
        assertEquals(2, columna(linea(b, "Total activo")));
        assertEquals(2, columna(linea(b, "Total pasivo y capital contable")));
    }

    @Test
    @DisplayName("Signo $ y rayas: $ en la primera cifra de cada columna; raya doble en los totales finales")
    void pesosYRayas() {
        BalanceGeneral b = generar(ejemplo());
        assertTrue(celda(linea(b, "Caja")).isPesos());
        assertFalse(celda(linea(b, "Bancos")).isPesos());
        assertEquals("simple", celda(linea(b, "Clientes")).getSubrayado());          // última del grupo
        assertTrue(celda(linea(b, "Total activo circulante")).isPesos());
        assertFalse(celda(linea(b, "Total activo no circulante")).isPesos());
        assertEquals("simple", celda(linea(b, "Total activo no circulante")).getSubrayado());
        assertTrue(celda(linea(b, "Total activo")).isPesos());
        assertEquals("doble", celda(linea(b, "Total activo")).getSubrayado());
        assertTrue(celda(linea(b, "Total pasivo")).isPesos());
        assertFalse(celda(linea(b, "Total capital contable")).isPesos());
        assertEquals("simple", celda(linea(b, "Total capital contable")).getSubrayado());
        assertTrue(celda(linea(b, "Total pasivo y capital contable")).isPesos());
        assertEquals("doble", celda(linea(b, "Total pasivo y capital contable")).getSubrayado());
    }

    @Test
    @DisplayName("La complementaria sale entre paréntesis (negativa)")
    void complementariaNegativa() {
        Celda c = celda(linea(generar(ejemplo()), "Depreciación acumulada de edificios"));
        assertTrue(c.isNegativo());
        assertEquals("(20,000.00)", c.getTexto());
    }

    @Test
    @DisplayName("Encabezado: empresa y fecha")
    void encabezado() {
        BalanceGeneral b = generar(ejemplo());
        assertEquals("Comercializadora del Valle, S.A. de C.V.", b.getEmpresa());
        assertEquals("Balance general", b.getTitulo());
        assertEquals("Al 31 de diciembre de 2026", b.getFechaTexto());
        assertEquals("8 de noviembre de 2026", b.getElaborado());
    }

    @Test
    @DisplayName("Sin cuentas: reporte vacío (y no marca diferencia)")
    void vacio() {
        BalanceGeneral b = generar(new ArrayList<>());
        assertTrue(b.isVacio());
        assertTrue(b.isCuadra());
        assertTrue(b.getPistas().isEmpty());
    }

    // ------------------------------------------------------------------ orden de las cuentas

    @Test
    @DisplayName("Orden alfabético, pero la depreciación queda pegada debajo de su bien")
    void ordenAlfabetico() {
        List<Saldo> l = new ArrayList<>();
        l.add(saldo("Terrenos", ACTIVO_NO_CIRCULANTE, "100"));
        l.add(saldo("Depreciación acumulada de equipo de transporte", ACTIVO_NO_CIRCULANTE, "10", true));
        l.add(saldo("Equipo de transporte", ACTIVO_NO_CIRCULANTE, "50"));
        l.add(saldo("Edificios", ACTIVO_NO_CIRCULANTE, "80"));
        List<String> nombres = servicio.agrupar(l, Empresa.ORDEN_ALFABETICO, catalogo()).get(ACTIVO_NO_CIRCULANTE)
                .stream().map(Saldo::getNombre).toList();
        assertEquals(List.of("Edificios", "Equipo de transporte", "Depreciación acumulada de equipo de transporte", "Terrenos"), nombres);
    }

    @Test
    @DisplayName("Orden del catálogo: de mayor a menor liquidez (Caja, Bancos, Clientes…)")
    void ordenCatalogo() {
        List<Saldo> l = new ArrayList<>();
        l.add(saldo("Clientes", ACTIVO_CIRCULANTE, "1"));
        l.add(saldo("Inventario de mercancías", ACTIVO_CIRCULANTE, "1"));
        l.add(saldo("Caja", ACTIVO_CIRCULANTE, "1"));
        l.add(saldo("Cuenta inventada", ACTIVO_CIRCULANTE, "1"));
        l.add(saldo("Bancos", ACTIVO_CIRCULANTE, "1"));
        List<String> nombres = servicio.agrupar(l, Empresa.ORDEN_CATALOGO, catalogo()).get(ACTIVO_CIRCULANTE)
                .stream().map(Saldo::getNombre).toList();
        assertEquals(List.of("Caja", "Bancos", "Clientes", "Inventario de mercancías", "Cuenta inventada"), nombres);
    }

    @Test
    @DisplayName("Orden de captura: como se fueron registrando")
    void ordenCaptura() {
        List<Saldo> l = new ArrayList<>();
        l.add(saldo("Clientes", ACTIVO_CIRCULANTE, "1"));
        l.add(saldo("Caja", ACTIVO_CIRCULANTE, "1"));
        List<String> nombres = servicio.agrupar(l, Empresa.ORDEN_CAPTURA, catalogo()).get(ACTIVO_CIRCULANTE)
                .stream().map(Saldo::getNombre).toList();
        assertEquals(List.of("Clientes", "Caja"), nombres);
    }

    @Test
    @DisplayName("Reconoce la cuenta a la que resta cada complementaria")
    void principal() {
        assertEquals("edificio", BalanceService.nombrePrincipal("Depreciación acumulada de edificios"));
        assertEquals("equipo de computo", BalanceService.nombrePrincipal("Depreciación acumulada de equipo de cómputo"));
        assertEquals("cliente", BalanceService.nombrePrincipal("Estimación para cuentas incobrables"));
        assertNull(BalanceService.nombrePrincipal("Amortización acumulada"));
        assertNull(BalanceService.nombrePrincipal("Pérdida del ejercicio"));
    }

    // ------------------------------------------------------------------ comparativo

    @Test
    @DisplayName("Comparativo: une las cuentas de las dos fechas y calcula la variación")
    void comparativo() {
        List<Saldo> actual = ejemplo();
        List<Saldo> anterior = new ArrayList<>();
        anterior.add(saldo("Caja", ACTIVO_CIRCULANTE, "8000"));
        anterior.add(saldo("Terrenos", ACTIVO_NO_CIRCULANTE, "92000"));
        anterior.add(saldo("Capital social", CAPITAL_CONTRIBUIDO, "100000"));
        Comparativo c = servicio.comparar(empresa(Empresa.ORDEN_CATALOGO), catalogo(),
                actual, FECHA, anterior, LocalDate.of(2025, 12, 31));
        assertEquals("2026", c.getEtiquetaActual());
        assertEquals("2025", c.getEtiquetaAnterior());
        assertTrue(c.isCuadraActual());
        assertTrue(c.isCuadraAnterior());

        Comparativo.Fila caja = c.getFilas().stream().filter(f -> f.getConcepto().equals("Caja")).findFirst().orElseThrow();
        assertEquals(pesos("2000"), caja.getVariacion().getImporte());
        assertEquals("+25.0 %", caja.getPorcentaje());

        Comparativo.Fila terrenos = c.getFilas().stream().filter(f -> f.getConcepto().equals("Terrenos")).findFirst().orElseThrow();
        assertEquals("—", terrenos.getActual().getTexto());     // en 2026 ya no está
        assertEquals("92,000.00", terrenos.getAnterior().getTexto());

        Comparativo.Fila total = c.getFilas().stream().filter(f -> f.getConcepto().equals("Total activo")).findFirst().orElseThrow();
        assertEquals(pesos("270000"), total.getActual().getImporte());
        assertEquals(pesos("100000"), total.getAnterior().getImporte());
    }

    // ------------------------------------------------------------------ balance dinámico (por periodo)

    @Test
    @DisplayName("Dinámico: suma las capturas de la misma cuenta dentro del periodo")
    void dinamicoSuma() {
        List<Saldo> l = new ArrayList<>();
        Saldo enero = saldo("Bancos", ACTIVO_CIRCULANTE, "40000");
        enero.setFecha(LocalDate.of(2026, 1, 15));
        Saldo junio = saldo("Bancos", ACTIVO_CIRCULANTE, "60000");
        junio.setFecha(LocalDate.of(2026, 6, 30));
        Saldo capital = saldo("Capital social", CAPITAL_CONTRIBUIDO, "100000");
        capital.setFecha(LocalDate.of(2026, 1, 15));
        l.add(enero);
        l.add(junio);
        l.add(capital);
        BalanceGeneral b = servicio.generar(empresa(Empresa.ORDEN_CATALOGO), l, catalogo(),
                LocalDate.of(2026, 1, 1), FECHA, HOY);
        assertTrue(b.isDinamico());
        assertEquals("Del 1 de enero al 31 de diciembre de 2026", b.getFechaTexto());
        assertEquals(pesos("100000"), celda(linea(b, "Bancos")).getImporte());
        assertEquals(1, b.getLineas().stream().filter(x -> x.getConcepto().equals("Bancos")).count());
        assertEquals(3, b.getRegistros());
        assertEquals(2, b.getNumCuentas());
        assertEquals(2, b.getNumFechas());
        assertTrue(b.isCuadra());
        assertEquals(pesos("40000"), enero.getImporte(), "No debe modificar las capturas originales");
    }

    @Test
    @DisplayName("Estático: el encabezado es «Al …» y no es dinámico")
    void estatico() {
        BalanceGeneral b = generar(ejemplo());
        assertFalse(b.isDinamico());
        assertNull(b.getInicio());
        assertEquals("Al 31 de diciembre de 2026", b.getFechaTexto());
    }

    @Test
    @DisplayName("Comparativo dinámico: dos periodos")
    void comparativoDinamico() {
        Comparativo c = servicio.comparar(empresa(Empresa.ORDEN_CATALOGO), catalogo(),
                ejemplo(), LocalDate.of(2026, 1, 1), FECHA,
                new ArrayList<>(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        assertEquals("2026", c.getEtiquetaActual());
        assertEquals("Del 1 de enero al 31 de diciembre de 2025", c.getFechaAnteriorTexto());
    }
}
