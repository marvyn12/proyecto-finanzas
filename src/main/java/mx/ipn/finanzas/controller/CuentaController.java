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

    // Módulo 5: Generar el reporte y mandarlo a la vista HTML
    @GetMapping("/")
    public String mostrarBalance(Model model) {
        // Pasamos la lista de cuentas y los totales a la página web
        model.addAttribute("cuentas", cuentaService.obtenerTodas());
        model.addAttribute("totalActivo", cuentaService.calcularTotalPorClasificacion("Activo"));
        model.addAttribute("totalPasivo", cuentaService.calcularTotalPorClasificacion("Pasivo"));
        model.addAttribute("totalCapital", cuentaService.calcularTotalPorClasificacion("Capital"));

        // Pasamos el resultado de la validación contable
        model.addAttribute("ecuacionValida", cuentaService.validarEcuacionContable());

        // Objeto vacío para el formulario de registro
        model.addAttribute("nuevaCuenta", new Cuenta());

        return "index"; // Esto buscará un archivo index.html en la carpeta templates
    }

    // Módulo 1: Registrar una cuenta desde el formulario y recargar la página
    @PostMapping("/guardar")
    public String guardarCuenta(Cuenta cuenta) {
        // La rúbrica dice omitir cantidades en cero en el reporte final,
        // pero podemos evitar que se guarden desde aquí por seguridad:
        if (cuenta.getImporte() != null && cuenta.getImporte() > 0) {
            cuentaService.guardarCuenta(cuenta);
        }
        return "redirect:/"; // Recarga la página principal para ver los cambios
    }
}