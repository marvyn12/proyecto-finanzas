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

    public List<Cuenta> obtenerTodas() {
        return cuentaRepository.findAll();
    }

    public Cuenta guardarCuenta(Cuenta cuenta) {
        return cuentaRepository.save(cuenta);
    }

    public void reiniciarEjercicio() {
        cuentaRepository.deleteAll();
    }

    public double calcularSuma(String clasificacion, String subclasificacion) {
        return cuentaRepository.findAll().stream()
                .filter(c -> c.getClasificacion().equalsIgnoreCase(clasificacion) &&
                        (subclasificacion == null || c.getSubclasificacion().equalsIgnoreCase(subclasificacion)))
                .mapToDouble(Cuenta::getImporte)
                .sum();
    }

    public double getVentas() { return calcularSuma("Ingreso", "Ventas"); }
    public double getCostos() { return calcularSuma("Egreso", "Costo"); }
    public double getGastos() { return calcularSuma("Egreso", "Gasto"); }

    public double getUtilidadBruta() { return getVentas() - getCostos(); }
    public double getUtilidadNeta() { return getUtilidadBruta() - getGastos(); }

    public boolean validarEcuacionContable() {
        double activo = calcularSuma("Activo", null);
        double pasivo = calcularSuma("Pasivo", null);
        double capital = calcularSuma("Capital", null);

        return Math.abs(activo - (pasivo + capital + getUtilidadNeta())) < 0.01;
    }
}