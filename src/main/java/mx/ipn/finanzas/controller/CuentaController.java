package mx.ipn.finanzas.controller;

import mx.ipn.finanzas.model.Cuenta;
import mx.ipn.finanzas.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class CuentaController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/")
    public String mostrarBalance(Model model) {
        model.addAttribute("cuentas", cuentaService.obtenerTodas());
        model.addAttribute("totalActivo", cuentaService.calcularSuma("Activo", null));
        model.addAttribute("totalPasivo", cuentaService.calcularSuma("Pasivo", null));
        model.addAttribute("totalCapital", cuentaService.calcularSuma("Capital", null));

        model.addAttribute("ventas", cuentaService.getVentas());
        model.addAttribute("costos", cuentaService.getCostos());
        model.addAttribute("utilidadBruta", cuentaService.getUtilidadBruta());
        model.addAttribute("gastos", cuentaService.getGastos());
        model.addAttribute("utilidadNeta", cuentaService.getUtilidadNeta());

        model.addAttribute("ecuacionValida", cuentaService.validarEcuacionContable());
        model.addAttribute("nuevaCuenta", new Cuenta());
        return "index";
    }

    @PostMapping("/guardar")
    public String guardarCuenta(Cuenta cuenta) {
        if (cuenta.getImporte() != null && cuenta.getImporte() > 0) {
            cuentaService.guardarCuenta(cuenta);
        }
        return "redirect:/";
    }

    @PostMapping("/reiniciar")
    public String reiniciarEjercicio() {
        cuentaService.reiniciarEjercicio();
        return "redirect:/";
    }
}