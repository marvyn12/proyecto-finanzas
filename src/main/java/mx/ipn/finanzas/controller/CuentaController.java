package mx.ipn.finanzas.controller;

import mx.ipn.finanzas.dto.ReporteFinanciero;
import mx.ipn.finanzas.model.Cuenta;
import mx.ipn.finanzas.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class CuentaController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/")
    public String mostrarEstado(Model model) {
        List<Cuenta> cuentas = cuentaService.obtenerTodas();
        ReporteFinanciero reporte = cuentaService.generarReporte();
        model.addAttribute("cuentas", cuentas);
        model.addAttribute("reporte", reporte);
        model.addAttribute("estado", reporte.getEstadoResultados());
        model.addAttribute("rubros", CuentaService.RUBROS);
        model.addAttribute("colores", CuentaService.COLORES);
        model.addAttribute("fuera", cuentaService.idsFueraDelEstado(cuentas));
        model.addAttribute("nuevaCuenta", new Cuenta());
        return "index";
    }

    /** Mismo reporte en JSON: lo usa el botón "Exportar PDF" para dibujar el formato clásico. */
    @GetMapping("/api/reporte")
    @ResponseBody
    public ReporteFinanciero reporteJson() {
        return cuentaService.generarReporte();
    }

    @PostMapping("/guardar")
    public String guardarCuenta(Cuenta cuenta, RedirectAttributes redirect) {
        boolean valida = cuenta.getImporte() != null && cuenta.getImporte() > 0
                && cuenta.getNombre() != null && !cuenta.getNombre().isBlank()
                && cuentaService.esRubroValido(cuenta.getSubclasificacion());
        if (valida) {
            cuentaService.guardarCuenta(cuenta);
            redirect.addFlashAttribute("mensaje", "Movimiento registrado: " + cuenta.getNombre()
                    + " por $" + ReporteFinanciero.Celda.formatear(cuenta.getImporte()));
        } else {
            redirect.addFlashAttribute("error", "Revisa la cuenta y el importe (debe ser mayor a cero).");
        }
        return "redirect:/";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarCuenta(@PathVariable Long id, RedirectAttributes redirect) {
        cuentaService.eliminarCuenta(id);
        redirect.addFlashAttribute("mensaje", "Movimiento eliminado.");
        return "redirect:/";
    }

    @PostMapping("/reiniciar")
    public String reiniciarEjercicio(RedirectAttributes redirect) {
        cuentaService.reiniciarEjercicio();
        redirect.addFlashAttribute("mensaje", "Ejercicio reiniciado. Puedes empezar desde cero.");
        return "redirect:/";
    }

    /** Tasas en porcentaje (33 = 33 %). */
    @PostMapping("/tasas")
    public String actualizarTasas(@RequestParam("isr") Double isr, @RequestParam("ptu") Double ptu,
                                  RedirectAttributes redirect) {
        if (isr == null || ptu == null || isr < 0 || isr > 100 || ptu < 0 || ptu > 100) {
            redirect.addFlashAttribute("error", "Las tasas deben estar entre 0 y 100 %.");
        } else {
            cuentaService.actualizarTasas(isr / 100.0, ptu / 100.0);
            redirect.addFlashAttribute("mensaje", "Tasas actualizadas: ISR " + ReporteFinanciero.porcentaje(isr / 100.0)
                    + " %, PTU " + ReporteFinanciero.porcentaje(ptu / 100.0) + " %.");
        }
        return "redirect:/";
    }
}