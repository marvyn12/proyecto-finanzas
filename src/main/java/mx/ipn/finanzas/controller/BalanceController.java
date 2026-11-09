package mx.ipn.finanzas.controller;

import jakarta.servlet.http.HttpSession;
import mx.ipn.finanzas.dto.BalanceGeneral;
import mx.ipn.finanzas.dto.Seleccion;
import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.CuentaCatalogo;
import mx.ipn.finanzas.model.Empresa;
import mx.ipn.finanzas.model.Saldo;
import mx.ipn.finanzas.model.Seccion;
import mx.ipn.finanzas.service.BalanceService;
import mx.ipn.finanzas.service.Clasificador;
import mx.ipn.finanzas.service.ContabilidadService;
import mx.ipn.finanzas.service.Fechas;
import mx.ipn.finanzas.service.Validador;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BalanceController {

    static final String SESION = "seleccionBalance";
    /** Avisos que se muestran en la siguiente carga de la página (para las llamadas con fetch). */
    static final String AVISO_PENDIENTE = "avisoPendiente";

    @Autowired
    private ContabilidadService contabilidad;

    @Autowired
    private BalanceService balances;

    // ================================================================== Página principal

    /**
     * Muestra el balance de la empresa elegida. Puede ser:
     *   estático (tipo=ESTATICO): las cuentas a una fecha → "Al 31 de diciembre de 2026"
     *   dinámico (tipo=DINAMICO): todo lo registrado en un periodo → "Del 1 de enero al 31 de diciembre de 2026"
     * Los parámetros son opcionales: si no vienen, se usa lo último que elegiste (se guarda en la sesión).
     */
    @GetMapping("/")
    public String inicio(@RequestParam(required = false) Long empresa,
                         @RequestParam(required = false) String tipo,
                         @RequestParam(required = false) String inicio,
                         @RequestParam(required = false) String fecha,
                         @RequestParam(required = false) String comparar,
                         @RequestParam(required = false) String inicio2,
                         @RequestParam(required = false) String fecha2,
                         HttpSession session, Model model) {

        avisoPendiente(session, model);
        List<Empresa> empresas = contabilidad.empresas();
        model.addAttribute("empresas", empresas);
        if (empresas.isEmpty()) {
            model.addAttribute("sinEmpresas", true);
            return "index";
        }

        Seleccion sel = seleccion(session);
        List<String> errores = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        // 1. Empresa
        Empresa actual = buscar(empresas, empresa);
        if (actual == null) actual = buscar(empresas, sel.getEmpresaId());
        if (actual == null) actual = empresas.get(0);
        if (!actual.getId().equals(sel.getEmpresaId()) || sel.getFecha() == null) {
            sel.setEmpresaId(actual.getId());
            sel.setFecha(Fechas.porDefecto(contabilidad.ultimaFecha(actual.getId()), hoy));
            sel.setInicio(null);
            sel.setInicio2(null);
            sel.setFecha2(null);
        }

        // 2. Estático o dinámico, y sus fechas
        if (tipo != null) sel.setDinamico("DINAMICO".equalsIgnoreCase(tipo));
        LocalDate f = fecha(fecha, "Fecha del balance", errores);
        LocalDate i = sel.isDinamico() ? fecha(inicio, "Inicio del periodo", errores) : null;
        if (f != null || i != null) {
            LocalDate nuevaF = f != null ? f : sel.getFecha();
            LocalDate nuevaI = i != null ? i : sel.getInicio();
            if (sel.isDinamico() && nuevaI != null && nuevaI.isAfter(nuevaF)) {
                errores.add("El periodo está al revés: el inicio (" + Fechas.corta(nuevaI) + ") es después del fin ("
                        + Fechas.corta(nuevaF) + "). Se queda el periodo anterior.");
            } else {
                sel.setFecha(nuevaF);
                if (i != null) sel.setInicio(nuevaI);
            }
        }
        if (!sel.isDinamico() || sel.getInicio() == null || sel.getInicio().isAfter(sel.getFecha())) {
            sel.setInicio(sel.getFecha().withDayOfYear(1));      // por defecto: del 1 de enero
        }

        // 3. Comparación
        if (comparar != null) sel.setComparar(Validador.casilla(comparar));
        LocalDate f2 = fecha(fecha2, "Fecha para comparar", errores);
        LocalDate i2 = sel.isDinamico() ? fecha(inicio2, "Inicio del periodo para comparar", errores) : null;
        if (f2 != null) sel.setFecha2(f2);
        if (i2 != null) sel.setInicio2(i2);
        boolean mismo = sel.getFecha2() != null && sel.getFecha2().equals(sel.getFecha())
                && (!sel.isDinamico() || sel.getInicio().equals(sel.getInicio2()));
        if (sel.getFecha2() == null || mismo) {
            sel.setFecha2(sel.getFecha().minusYears(1));
            if (mismo) sel.setInicio2(sel.getInicio().minusYears(1));
        }
        if (sel.getInicio2() == null || sel.getInicio2().isAfter(sel.getFecha2())) {
            if (i2 != null) errores.add("El periodo para comparar está al revés; se usa el mismo periodo un año antes.");
            sel.setInicio2(sel.getInicio().minusYears(1));
            if (sel.getInicio2().isAfter(sel.getFecha2())) sel.setInicio2(sel.getFecha2().withDayOfYear(1));
        }
        if (!sel.isDinamico()) sel.setInicio2(sel.getFecha2().withDayOfYear(1));

        // 4. Datos
        Long id = actual.getId();
        List<CuentaCatalogo> catalogo = contabilidad.catalogo(id);
        List<Saldo> saldos = saldosDe(sel, id, false);
        BalanceGeneral balance = balanceDe(sel, actual, saldos, catalogo, hoy);

        model.addAttribute("empresa", actual);
        model.addAttribute("sel", sel);
        model.addAttribute("balance", balance);
        model.addAttribute("saldos", ordenados(balances.agrupar(saldos, actual.getOrdenCuentas(), catalogo)));
        model.addAttribute("fechas", contabilidad.fechas(id));
        model.addAttribute("catalogo", catalogo);
        model.addAttribute("catalogoPorClasificacion", porClasificacion(catalogo));
        model.addAttribute("conteo", contabilidad.conteoPorCuenta(id));
        model.addAttribute("secciones", Seccion.values());
        model.addAttribute("clasificaciones", Clasificacion.values());
        model.addAttribute("dinamico", sel.isDinamico());
        model.addAttribute("fechaIso", sel.getFecha().toString());
        model.addAttribute("fechaTexto", Fechas.corta(sel.getFecha()));
        model.addAttribute("inicioIso", sel.getInicio().toString());
        model.addAttribute("inicioTexto", Fechas.corta(sel.getInicio()));
        model.addAttribute("periodoTexto", textoPeriodo(sel));
        model.addAttribute("anteriorTexto", "del " + Fechas.corta(sel.getInicio().minusYears(1)) + " al "
                + Fechas.corta(sel.getFecha().minusYears(1)));
        model.addAttribute("fecha2Iso", sel.getFecha2().toString());
        model.addAttribute("inicio2Iso", sel.getInicio2().toString());
        model.addAttribute("registroSugerido", registroSugerido(sel, hoy).toString());

        if (sel.isComparar()) model.addAttribute("comparativo", comparativoDe(sel, actual, saldos, catalogo));
        if (!errores.isEmpty()) model.addAttribute("errorFecha", String.join(" ", errores));
        return "index";
    }

    // ================================================================== Empresas

    @PostMapping("/empresas")
    public String crearEmpresa(@RequestParam(required = false) String nombre, HttpSession session, RedirectAttributes r) {
        try {
            Empresa e = contabilidad.crearEmpresa(nombre);
            Seleccion sel = seleccion(session);
            sel.setEmpresaId(e.getId());
            sel.setFecha(null);
            r.addFlashAttribute("mensaje", "Empresa creada: «" + e.getNombre() + "». Ya tiene su catálogo de cuentas.");
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/empresas/configurar")
    public String configurarEmpresa(@RequestParam(required = false) String nombre,
                                    @RequestParam(required = false) String orden,
                                    HttpSession session, RedirectAttributes r) {
        try {
            Empresa e = contabilidad.configurarEmpresa(seleccion(session).getEmpresaId(), nombre, orden);
            r.addFlashAttribute("mensaje", "Cambios guardados: " + e.getNombre() + " · orden " + e.getOrdenTexto() + ".");
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/empresas/eliminar")
    public String eliminarEmpresa(HttpSession session, RedirectAttributes r) {
        Seleccion sel = seleccion(session);
        try {
            String nombre = contabilidad.empresa(sel.getEmpresaId()).map(Empresa::getNombre).orElse("");
            contabilidad.eliminarEmpresa(sel.getEmpresaId());
            sel.setEmpresaId(null);
            sel.setFecha(null);
            r.addFlashAttribute("mensaje", "Empresa eliminada: " + nombre);
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    // ================================================================== Cuentas del balance

    /** Registra (sin id) o edita (con id) una cuenta en el balance. */
    @PostMapping("/saldos")
    public String guardarSaldo(@RequestParam(required = false) Long id,
                               @RequestParam(required = false) String fecha,
                               @RequestParam(required = false) Long cuentaId,
                               @RequestParam(required = false) String importe,
                               @RequestParam(required = false) String comentario,
                               HttpSession session, RedirectAttributes r) {
        Seleccion sel = seleccion(session);
        try {
            Saldo s = id == null
                    ? contabilidad.registrar(sel.getEmpresaId(), fecha, cuentaId, importe, comentario)
                    : contabilidad.editar(sel.getEmpresaId(), id, fecha, cuentaId, importe, comentario);
            avisoGuardado(s, id == null ? "Registrada" : "Actualizada", sel, r);
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
            if (id != null) r.addFlashAttribute("pestana", "#pane-CTA");
        }
        return "redirect:/";
    }

    @PostMapping("/saldos/{id}/eliminar")
    public String eliminarSaldo(@PathVariable Long id, HttpSession session, RedirectAttributes r) {
        try {
            Saldo s = contabilidad.eliminar(seleccion(session).getEmpresaId(), id);
            r.addFlashAttribute("mensaje", "Quitada del balance: " + s.getNombre() + " (" + s.getImporteTexto() + ").");
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
        }
        r.addFlashAttribute("pestana", "#pane-CTA");
        return "redirect:/";
    }

    /** Borra todas las cuentas del balance que estás viendo (la fecha o todo el periodo). */
    @PostMapping("/balance/borrar")
    public String borrarBalance(HttpSession session, RedirectAttributes r) {
        Seleccion sel = seleccion(session);
        if (sel.getEmpresaId() == null || sel.getFecha() == null) return "redirect:/";
        int n = sel.isDinamico()
                ? contabilidad.borrarPeriodo(sel.getEmpresaId(), sel.getInicio(), sel.getFecha())
                : contabilidad.borrarBalance(sel.getEmpresaId(), sel.getFecha());
        r.addFlashAttribute("mensaje", "Se borraron " + n + " cuentas registradas " + textoPeriodo(sel) + ".");
        return "redirect:/";
    }

    /** Copia a la fecha que estás viendo las cuentas del balance de otra fecha. */
    @PostMapping("/balance/copiar")
    public String copiarBalance(@RequestParam(required = false) String origen,
                                @RequestParam(required = false) String modo,
                                HttpSession session, RedirectAttributes r) {
        Seleccion sel = seleccion(session);
        try {
            boolean periodo = sel.isDinamico() && "PERIODO".equals(modo);
            ContabilidadService.Copia c = periodo
                    ? contabilidad.copiarPeriodoAnterior(sel.getEmpresaId(), sel.getInicio(), sel.getFecha())
                    : contabilidad.copiarBalance(sel.getEmpresaId(), origen, sel.getFecha());
            String texto = periodo
                    ? "Se copiaron " + c.copiadas() + " registros del año anterior, con las mismas fechas un año después."
                    : "Se copiaron " + c.copiadas() + " cuentas al balance del " + Fechas.corta(sel.getFecha()) + ".";
            if (c.yaEstaban() > 0) texto += " " + c.yaEstaban() + " ya estaban y no se tocaron.";
            r.addFlashAttribute(c.copiadas() == 0 ? "alerta" : "mensaje", texto + (c.copiadas() > 0 ? " Ahora ajusta los importes." : ""));
            r.addFlashAttribute("pestana", "#pane-CTA");
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    // ================================================================== Catálogo

    /**
     * Cuenta nueva (nombre, clasificación, si resta y, opcional, importe). La llama el modal con fetch:
     * si hay error se muestra dentro del modal sin perder lo escrito; si sale bien la página se recarga.
     */
    @PostMapping("/api/cuentas")
    @ResponseBody
    public Map<String, Object> cuentaNueva(@RequestParam(required = false) String nombre,
                                           @RequestParam(required = false) String clasificacion,
                                           @RequestParam(required = false) String resta,
                                           @RequestParam(required = false) String importe,
                                           @RequestParam(required = false) String comentario,
                                           @RequestParam(required = false) String fecha,
                                           HttpSession session) {
        Map<String, Object> res = new LinkedHashMap<>();
        Seleccion sel = seleccion(session);
        try {
            ContabilidadService.CuentaNueva n = contabilidad.cuentaNueva(sel.getEmpresaId(), fecha, nombre, clasificacion,
                    Validador.casilla(resta), importe, comentario);
            CuentaCatalogo c = n.agregada().cuenta();
            String texto = (n.agregada().nueva() ? "Cuenta nueva en el catálogo: " : "La cuenta ya estaba en el catálogo: ")
                    + c.getNombre() + " (" + c.getClasificacion().getNombre() + (c.isResta() ? ", resta" : "") + ").";
            if (n.saldo() != null) {
                texto += " Registrada en el balance al " + n.saldo().getFechaTexto() + " con " + n.saldo().getImporteTexto() + ".";
                if (n.saldo().isEnCero()) texto += " Como está en $0.00 no sale en el reporte.";
            }
            session.setAttribute(AVISO_PENDIENTE, Map.of("mensaje", texto));
            res.put("ok", true);
            res.put("id", c.getId());
        } catch (IllegalArgumentException ex) {
            res.put("ok", false);
            res.put("mensaje", ex.getMessage());
        }
        return res;
    }

    /** Sugiere la clasificación de una cuenta según su nombre (módulo 2). */
    @GetMapping("/api/sugerir")
    @ResponseBody
    public Map<String, Object> sugerir(@RequestParam(required = false) String nombre) {
        Map<String, Object> res = new LinkedHashMap<>();
        Clasificador.sugerir(nombre).ifPresent(s -> {
            res.put("clasificacion", s.clasificacion().name());
            res.put("etiqueta", s.clasificacion().getEtiqueta());
            res.put("resta", s.resta());
            res.put("motivo", s.motivo());
        });
        return res;
    }

    @PostMapping("/catalogo/{id}/eliminar")
    public String eliminarCuenta(@PathVariable Long id, HttpSession session, RedirectAttributes r) {
        try {
            CuentaCatalogo c = contabilidad.eliminarCuenta(seleccion(session).getEmpresaId(), id);
            r.addFlashAttribute("mensaje", "Cuenta quitada del catálogo: " + c.getNombre() + ".");
        } catch (IllegalArgumentException ex) {
            r.addFlashAttribute("error", ex.getMessage());
        }
        r.addFlashAttribute("pestana", "#pane-CAT");
        return "redirect:/";
    }

    // ================================================================== JSON para el PDF

    @GetMapping("/api/balance")
    @ResponseBody
    public ResponseEntity<?> balanceJson(HttpSession session) {
        Seleccion sel = seleccion(session);
        Empresa e = contabilidad.empresa(sel.getEmpresaId()).orElse(null);
        if (e == null || sel.getFecha() == null) return ResponseEntity.badRequest().body(Map.of("mensaje", "Recarga la página."));
        return ResponseEntity.ok(balanceDe(sel, e, saldosDe(sel, e.getId(), false), contabilidad.catalogo(e.getId()), LocalDate.now()));
    }

    @GetMapping("/api/comparativo")
    @ResponseBody
    public ResponseEntity<?> comparativoJson(HttpSession session) {
        Seleccion sel = seleccion(session);
        Empresa e = contabilidad.empresa(sel.getEmpresaId()).orElse(null);
        if (e == null || sel.getFecha() == null || !sel.isComparar() || sel.getFecha2() == null) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Activa \"Comparar con otra fecha\" y recarga la página."));
        }
        return ResponseEntity.ok(comparativoDe(sel, e, saldosDe(sel, e.getId(), false), contabilidad.catalogo(e.getId())));
    }

    // ================================================================== Utilidades

    static Seleccion seleccion(HttpSession session) {
        Seleccion sel = (Seleccion) session.getAttribute(SESION);
        if (sel == null) {
            sel = new Seleccion();
            session.setAttribute(SESION, sel);
        }
        return sel;
    }

    @SuppressWarnings("unchecked")
    private static void avisoPendiente(HttpSession session, Model model) {
        Object aviso = session.getAttribute(AVISO_PENDIENTE);
        if (aviso instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).forEach(model::addAttribute);
            session.removeAttribute(AVISO_PENDIENTE);
        }
    }

    private static void avisoGuardado(Saldo s, String accion, Seleccion sel, RedirectAttributes r) {
        String texto = accion + ": " + s.getNombre() + " por " + s.getImporteTexto() + " al " + s.getFechaTexto() + ".";
        boolean visible = sel.isDinamico()
                ? !s.getFecha().isBefore(sel.getInicio()) && !s.getFecha().isAfter(sel.getFecha())
                : s.getFecha().equals(sel.getFecha());
        if (!visible) {
            r.addFlashAttribute("alerta", texto + " Ojo: esa fecha queda fuera del balance que estás viendo ("
                    + textoPeriodo(sel) + ").");
        } else if (s.isEnCero()) {
            r.addFlashAttribute("alerta", texto + " Como está en $0.00 no aparece en el reporte.");
        } else {
            r.addFlashAttribute("mensaje", texto);
        }
    }

    /** Lo registrado en la fecha (estático) o en el periodo (dinámico); comparacion=true para el de comparar. */
    private List<Saldo> saldosDe(Seleccion sel, Long empresaId, boolean comparacion) {
        LocalDate f = comparacion ? sel.getFecha2() : sel.getFecha();
        LocalDate i = comparacion ? sel.getInicio2() : sel.getInicio();
        return sel.isDinamico() ? contabilidad.saldosPeriodo(empresaId, i, f) : contabilidad.saldos(empresaId, f);
    }

    private BalanceGeneral balanceDe(Seleccion sel, Empresa e, List<Saldo> saldos, List<CuentaCatalogo> catalogo, LocalDate hoy) {
        return balances.generar(e, saldos, catalogo, sel.isDinamico() ? sel.getInicio() : null, sel.getFecha(), hoy);
    }

    private mx.ipn.finanzas.dto.Comparativo comparativoDe(Seleccion sel, Empresa e, List<Saldo> saldos, List<CuentaCatalogo> catalogo) {
        List<Saldo> otros = saldosDe(sel, e.getId(), true);
        return sel.isDinamico()
                ? balances.comparar(e, catalogo, saldos, sel.getInicio(), sel.getFecha(), otros, sel.getInicio2(), sel.getFecha2())
                : balances.comparar(e, catalogo, saldos, sel.getFecha(), otros, sel.getFecha2());
    }

    /** "al 31/12/2026" o "del 01/01/2026 al 31/12/2026" */
    static String textoPeriodo(Seleccion sel) {
        return sel.isDinamico()
                ? "del " + Fechas.corta(sel.getInicio()) + " al " + Fechas.corta(sel.getFecha())
                : "al " + Fechas.corta(sel.getFecha());
    }

    /** Fecha que se propone al registrar en un balance dinámico: hoy si cae en el periodo; si no, el fin. */
    private static LocalDate registroSugerido(Seleccion sel, LocalDate hoy) {
        if (sel.isDinamico() && !hoy.isBefore(sel.getInicio()) && !hoy.isAfter(sel.getFecha())) return hoy;
        return sel.getFecha();
    }

    /** Lee una fecha opcional; si viene mal agrega el error y regresa null. */
    private static LocalDate fecha(String valor, String que, List<String> errores) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Validador.fecha(valor);
        } catch (IllegalArgumentException ex) {
            errores.add(que + ": " + ex.getMessage() + " Se queda la anterior.");
            return null;
        }
    }

    private static Empresa buscar(List<Empresa> empresas, Long id) {
        if (id == null) return null;
        for (Empresa e : empresas) if (e.getId().equals(id)) return e;
        return null;
    }

    /** Las cuentas registradas en el mismo orden que el reporte (clasificación y orden de la empresa). */
    private static List<Saldo> ordenados(Map<Clasificacion, List<Saldo>> grupos) {
        List<Saldo> l = new ArrayList<>();
        grupos.values().forEach(l::addAll);
        return l;
    }

    private static Map<String, List<CuentaCatalogo>> porClasificacion(List<CuentaCatalogo> catalogo) {
        Map<String, List<CuentaCatalogo>> m = new LinkedHashMap<>();
        for (Clasificacion c : Clasificacion.values()) m.put(c.name(), new ArrayList<>());
        for (CuentaCatalogo c : catalogo) m.get(c.getClasificacion().name()).add(c);
        return m;
    }
}