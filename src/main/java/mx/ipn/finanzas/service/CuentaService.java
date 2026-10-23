package mx.ipn.finanzas.service;

import mx.ipn.finanzas.model.Cuenta;
import mx.ipn.finanzas.repository.CuentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CuentaService {

    @Autowired
    private CuentaRepository cuentaRepository;

    // Obtener todas las cuentas registradas
    public List<Cuenta> obtenerTodas() {
        return cuentaRepository.findAll();
    }

    // Guardar una nueva cuenta en la base de datos
    public Cuenta guardarCuenta(Cuenta cuenta) {
        return cuentaRepository.save(cuenta);
    }

    // Módulo 3: Cálculo automático de sumas por clasificación
    public double calcularTotalPorClasificacion(String clasificacion) {
        List<Cuenta> cuentas = cuentaRepository.findAll();

        return cuentas.stream()
                .filter(c -> c.getClasificacion().equalsIgnoreCase(clasificacion))
                .mapToDouble(Cuenta::getImporte)
                .sum();
    }

    // Módulo 4: Validación de la ecuación contable (Activo = Pasivo + Capital)
    public boolean validarEcuacionContable() {
        double totalActivo = calcularTotalPorClasificacion("Activo");
        double totalPasivo = calcularTotalPorClasificacion("Pasivo");
        double totalCapital = calcularTotalPorClasificacion("Capital");

        // Usamos Math.abs con un margen mínimo (0.01) por si hay decimales que causen problemas de redondeo en Java
        return Math.abs(totalActivo - (totalPasivo + totalCapital)) < 0.01;
    }
}