package mx.ipn.finanzas.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Una fecha que ya tiene balance capturado, con cuántas cuentas tiene. */
public record FechaBalance(LocalDate fecha, long cuentas) {

    public String getIso() { return fecha.toString(); }

    public String getTexto() { return fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    public long getCuentas() { return cuentas; }
}
