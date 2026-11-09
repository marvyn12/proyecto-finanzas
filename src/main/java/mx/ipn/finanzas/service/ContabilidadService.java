package mx.ipn.finanzas.service;

import mx.ipn.finanzas.dto.FechaBalance;
import mx.ipn.finanzas.dto.Formato;
import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.CuentaCatalogo;
import mx.ipn.finanzas.model.Empresa;
import mx.ipn.finanzas.model.Saldo;
import mx.ipn.finanzas.repository.CuentaCatalogoRepository;
import mx.ipn.finanzas.repository.EmpresaRepository;
import mx.ipn.finanzas.repository.SaldoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guarda y valida: empresas, catálogo de cuentas y las cuentas registradas en cada balance (módulo 1).
 * Cualquier dato inválido lanza IllegalArgumentException con un mensaje listo para mostrar.
 */
@Service
public class ContabilidadService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private SaldoRepository saldoRepository;

    @Autowired
    private CuentaCatalogoRepository catalogoRepository;

    // ================================================================== Empresas

    public List<Empresa> empresas() {
        return empresaRepository.findAllByOrderByNombreAsc();
    }

    public Optional<Empresa> empresa(Long id) {
        return id == null ? Optional.empty() : empresaRepository.findById(id);
    }

    /** Crea la empresa y le copia el catálogo base de cuentas. */
    @Transactional
    public Empresa crearEmpresa(String nombre) {
        String limpio = Validador.texto(nombre, "el nombre de la empresa", Validador.MAX_NOMBRE);
        if (empresaRepository.existsByNombreIgnoreCase(limpio)) {
            throw new IllegalArgumentException("Ya existe una empresa llamada \"" + limpio + "\".");
        }
        Empresa e = new Empresa();
        e.setNombre(limpio);
        e = empresaRepository.save(e);

        int orden = 0;
        for (CatalogoBase.Cuenta base : CatalogoBase.CUENTAS) {
            CuentaCatalogo c = new CuentaCatalogo();
            c.setEmpresa(e);
            c.setNombre(base.nombre());
            c.setClasificacion(base.clasificacion());
            c.setResta(base.resta());
            c.setOrden(++orden);
            catalogoRepository.save(c);
        }
        return e;
    }

    /** Cambia el nombre y el orden de las cuentas. */
    @Transactional
    public Empresa configurarEmpresa(Long id, String nombre, String orden) {
        Empresa e = empresa(id).orElseThrow(() -> new IllegalArgumentException("Esa empresa ya no existe."));
        String limpio = Validador.texto(nombre, "el nombre de la empresa", Validador.MAX_NOMBRE);
        if (empresaRepository.existsByNombreIgnoreCaseAndIdNot(limpio, id)) {
            throw new IllegalArgumentException("Ya existe otra empresa llamada \"" + limpio + "\".");
        }
        e.setNombre(limpio);
        e.setOrdenCuentas(Validador.orden(orden));
        return empresaRepository.save(e);
    }

    /** Borra la empresa con todos sus balances y su catálogo. */
    @Transactional
    public void eliminarEmpresa(Long id) {
        Empresa e = empresa(id).orElseThrow(() -> new IllegalArgumentException("Esa empresa ya no existe."));
        saldoRepository.borrarDeEmpresa(e.getId());
        catalogoRepository.borrarDeEmpresa(e.getId());
        empresaRepository.delete(e);
    }

    // ================================================================== Catálogo de cuentas

    public List<CuentaCatalogo> catalogo(Long empresaId) {
        return catalogoRepository.findByEmpresaIdOrderByOrdenAscIdAsc(empresaId);
    }

    /** Resultado de agregar una cuenta al catálogo: la cuenta y si era nueva o ya existía. */
    public record CuentaAgregada(CuentaCatalogo cuenta, boolean nueva) {}

    @Transactional
    public CuentaAgregada agregarCuenta(Long empresaId, String nombre, String clasificacion, boolean resta) {
        Empresa e = empresa(empresaId).orElseThrow(() -> new IllegalArgumentException("Primero elige una empresa."));
        String limpio = Validador.texto(nombre, "el nombre de la cuenta", Validador.MAX_NOMBRE);
        Clasificacion c = Validador.clasificacion(clasificacion);
        Optional<CuentaCatalogo> existente =
                catalogoRepository.findFirstByEmpresaIdAndClasificacionAndNombreIgnoreCase(empresaId, c, limpio);
        if (existente.isPresent()) {
            if (existente.get().isResta() != resta) {
                throw new IllegalArgumentException("«" + existente.get().getNombre() + "» ya está en " + c.getNombre()
                        + (existente.get().isResta() ? " como cuenta que resta." : " como cuenta que suma.")
                        + " Usa esa o ponle otro nombre.");
            }
            return new CuentaAgregada(existente.get(), false);
        }
        CuentaCatalogo nueva = new CuentaCatalogo();
        nueva.setEmpresa(e);
        nueva.setNombre(limpio);
        nueva.setClasificacion(c);
        nueva.setResta(resta);
        nueva.setOrden(catalogoRepository.maxOrden(empresaId) + 1);
        return new CuentaAgregada(catalogoRepository.save(nueva), true);
    }

    /** Quita la cuenta del catálogo. Los balances donde ya se usó NO cambian. */
    @Transactional
    public CuentaCatalogo eliminarCuenta(Long empresaId, Long cuentaId) {
        CuentaCatalogo c = catalogoRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no está en el catálogo."));
        catalogoRepository.delete(c);
        return c;
    }

    /** En cuántos balances se usa cada cuenta (llave: "CLASIFICACION|nombre en minúsculas"). */
    public Map<String, Long> conteoPorCuenta(Long empresaId) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] fila : saldoRepository.conteoPorCuenta(empresaId)) {
            m.put(fila[0] + "|" + fila[1], ((Number) fila[2]).longValue());
        }
        return m;
    }

    // ================================================================== Balances (cuentas registradas por fecha)

    public List<Saldo> saldos(Long empresaId, LocalDate fecha) {
        return saldoRepository.findByEmpresaIdAndFechaOrderByIdAsc(empresaId, fecha);
    }

    /** Balance dinámico: todo lo registrado entre dos fechas. */
    public List<Saldo> saldosPeriodo(Long empresaId, LocalDate desde, LocalDate hasta) {
        return saldoRepository.findByEmpresaIdAndFechaBetweenOrderByFechaAscIdAsc(empresaId, desde, hasta);
    }

    public LocalDate ultimaFecha(Long empresaId) {
        return saldoRepository.ultimaFecha(empresaId);
    }

    /** Fechas que ya tienen balance, de la más reciente a la más antigua. */
    public List<FechaBalance> fechas(Long empresaId) {
        List<FechaBalance> l = new ArrayList<>();
        for (Object[] fila : saldoRepository.fechas(empresaId)) {
            l.add(new FechaBalance((LocalDate) fila[0], ((Number) fila[1]).longValue()));
        }
        return l;
    }

    /** Registra en el balance de una fecha una cuenta que ya está en el catálogo. */
    @Transactional
    public Saldo registrar(Long empresaId, String fecha, Long cuentaId, String importe, String comentario) {
        Empresa e = empresa(empresaId).orElseThrow(() -> new IllegalArgumentException("Primero elige una empresa."));
        if (cuentaId == null) throw new IllegalArgumentException("Elige una cuenta del catálogo.");
        CuentaCatalogo c = catalogoRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no está en el catálogo. Recarga la página."));
        Saldo s = new Saldo();
        s.setEmpresa(e);
        s.setFecha(Validador.fecha(fecha));
        s.setNombre(c.getNombre());
        s.setClasificacion(c.getClasificacion());
        s.setResta(c.isResta());
        s.setImporte(Validador.importe(importe));
        s.setComentario(Validador.comentario(comentario));
        revisarRepetida(empresaId, s.getFecha(), s.getClasificacion(), s.getNombre(), null);
        return saldoRepository.save(s);
    }

    /** Resultado de dar de alta una cuenta nueva: la cuenta del catálogo y, si traía importe, lo registrado. */
    public record CuentaNueva(CuentaAgregada agregada, Saldo saldo) {}

    /**
     * Registro de cuentas adicionales: agrega la cuenta al catálogo (nombre + clasificación) y, si trae importe,
     * la registra de una vez en el balance de la fecha. Todo o nada: si algo falla no se guarda nada.
     */
    @Transactional
    public CuentaNueva cuentaNueva(Long empresaId, String fecha, String nombre, String clasificacion, boolean resta,
                                   String importe, String comentario) {
        // Se valida todo ANTES de guardar, para no dejar la cuenta a medias
        boolean conImporte = importe != null && !importe.isBlank();
        LocalDate f = conImporte ? Validador.fecha(fecha) : null;
        BigDecimal valor = conImporte ? Validador.importe(importe) : null;
        String coment = Validador.comentario(comentario);
        String limpio = Validador.texto(nombre, "el nombre de la cuenta", Validador.MAX_NOMBRE);
        Clasificacion c = Validador.clasificacion(clasificacion);
        if (conImporte) revisarRepetida(empresaId, f, c, limpio, null);

        CuentaAgregada a = agregarCuenta(empresaId, nombre, clasificacion, resta);
        if (!conImporte) return new CuentaNueva(a, null);
        Saldo s = new Saldo();
        s.setEmpresa(a.cuenta().getEmpresa());
        s.setFecha(f);
        s.setNombre(a.cuenta().getNombre());
        s.setClasificacion(a.cuenta().getClasificacion());
        s.setResta(a.cuenta().isResta());
        s.setImporte(valor);
        s.setComentario(coment);
        return new CuentaNueva(a, saldoRepository.save(s));
    }

    /** Edita una cuenta registrada. Si cuentaId viene vacío se queda con la misma cuenta. */
    @Transactional
    public Saldo editar(Long empresaId, Long id, String fecha, Long cuentaId, String importe, String comentario) {
        Saldo s = saldoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no está en el balance."));
        LocalDate f = Validador.fecha(fecha);
        BigDecimal valor = Validador.importe(importe);
        String coment = Validador.comentario(comentario);
        CuentaCatalogo c = null;
        if (cuentaId != null) {
            c = catalogoRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no está en el catálogo. Recarga la página."));
        }
        // Se revisa ANTES de cambiar nada, para no dejar la cuenta a medias si está repetida
        revisarRepetida(empresaId, f, c != null ? c.getClasificacion() : s.getClasificacion(),
                c != null ? c.getNombre() : s.getNombre(), s.getId());
        if (c != null) {
            s.setNombre(c.getNombre());
            s.setClasificacion(c.getClasificacion());
            s.setResta(c.isResta());
        }
        s.setFecha(f);
        s.setImporte(valor);
        s.setComentario(coment);
        return saldoRepository.save(s);
    }

    @Transactional
    public Saldo eliminar(Long empresaId, Long id) {
        Saldo s = saldoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no está en el balance."));
        saldoRepository.delete(s);
        return s;
    }

    /** Borra todas las cuentas del balance de una fecha. */
    @Transactional
    public int borrarBalance(Long empresaId, LocalDate fecha) {
        return saldoRepository.borrarDeFecha(empresaId, fecha);
    }

    /** Borra todo lo registrado dentro de un periodo (balance dinámico). */
    @Transactional
    public int borrarPeriodo(Long empresaId, LocalDate desde, LocalDate hasta) {
        return saldoRepository.borrarDePeriodo(empresaId, desde, hasta);
    }

    /** Resultado de copiar un balance: cuántas cuentas se copiaron y cuántas ya estaban. */
    public record Copia(int copiadas, int yaEstaban) {}

    /** Copia las cuentas (con sus importes) del balance de otra fecha al de esta fecha. Las que ya estén no se tocan. */
    @Transactional
    public Copia copiarBalance(Long empresaId, String origen, LocalDate destino) {
        Empresa e = empresa(empresaId).orElseThrow(() -> new IllegalArgumentException("Primero elige una empresa."));
        LocalDate o = Validador.fecha(origen);
        if (o.equals(destino)) throw new IllegalArgumentException("Elige una fecha distinta a la del balance que estás viendo.");
        List<Saldo> fuente = saldos(empresaId, o);
        if (fuente.isEmpty()) throw new IllegalArgumentException("No hay cuentas registradas al " + Fechas.corta(o) + ".");
        int copiadas = 0, yaEstaban = 0;
        for (Saldo s : fuente) {
            if (!saldoRepository.findByEmpresaIdAndFechaAndClasificacionAndNombreIgnoreCase(
                    empresaId, destino, s.getClasificacion(), s.getNombre()).isEmpty()) {
                yaEstaban++;
                continue;
            }
            Saldo n = new Saldo();
            n.setEmpresa(e);
            n.setFecha(destino);
            n.setNombre(s.getNombre());
            n.setClasificacion(s.getClasificacion());
            n.setResta(s.isResta());
            n.setImporte(s.getImporte());
            n.setComentario(s.getComentario());
            saldoRepository.save(n);
            copiadas++;
        }
        return new Copia(copiadas, yaEstaban);
    }

    /**
     * Balance dinámico: copia todos los registros del mismo periodo un año antes, con las mismas fechas
     * un año después (p. ej. lo del 10/01/2024 queda el 10/01/2025). Los que ya estén no se tocan.
     */
    @Transactional
    public Copia copiarPeriodoAnterior(Long empresaId, LocalDate inicio, LocalDate fin) {
        Empresa e = empresa(empresaId).orElseThrow(() -> new IllegalArgumentException("Primero elige una empresa."));
        LocalDate oi = inicio.minusYears(1), of = fin.minusYears(1);
        List<Saldo> fuente = saldosPeriodo(empresaId, oi, of);
        if (fuente.isEmpty()) {
            throw new IllegalArgumentException("No hay registros del " + Fechas.corta(oi) + " al " + Fechas.corta(of) + " para copiar.");
        }
        int copiadas = 0, yaEstaban = 0;
        for (Saldo s : fuente) {
            LocalDate nueva = s.getFecha().plusYears(1);
            if (nueva.isBefore(inicio)) nueva = inicio;
            if (nueva.isAfter(fin)) nueva = fin;
            if (!saldoRepository.findByEmpresaIdAndFechaAndClasificacionAndNombreIgnoreCase(
                    empresaId, nueva, s.getClasificacion(), s.getNombre()).isEmpty()) {
                yaEstaban++;
                continue;
            }
            Saldo n = new Saldo();
            n.setEmpresa(e);
            n.setFecha(nueva);
            n.setNombre(s.getNombre());
            n.setClasificacion(s.getClasificacion());
            n.setResta(s.isResta());
            n.setImporte(s.getImporte());
            n.setComentario(s.getComentario());
            saldoRepository.save(n);
            copiadas++;
        }
        return new Copia(copiadas, yaEstaban);
    }

    /** Una cuenta solo puede aparecer una vez en cada balance (misma fecha y clasificación). */
    private void revisarRepetida(Long empresaId, LocalDate fecha, Clasificacion clasificacion, String nombre, Long idPropio) {
        for (Saldo otro : saldoRepository.findByEmpresaIdAndFechaAndClasificacionAndNombreIgnoreCase(
                empresaId, fecha, clasificacion, nombre)) {
            if (!otro.getId().equals(idPropio)) {
                throw new IllegalArgumentException("«" + otro.getNombre() + "» ya está en el balance al "
                        + Fechas.corta(fecha) + " con " + Formato.pesos(otro.getValor())
                        + ". Si cambió el importe, edítala con el lápiz en la pestaña «Cuentas registradas».");
            }
        }
    }
}