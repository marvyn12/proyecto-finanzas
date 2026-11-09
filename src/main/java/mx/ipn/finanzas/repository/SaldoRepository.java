package mx.ipn.finanzas.repository;

import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.Saldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaldoRepository extends JpaRepository<Saldo, Long> {

    /** Las cuentas del balance de una fecha. */
    List<Saldo> findByEmpresaIdAndFechaOrderByIdAsc(Long empresaId, LocalDate fecha);

    /** Las cuentas registradas dentro de un periodo (balance dinámico; incluye ambas fechas). */
    List<Saldo> findByEmpresaIdAndFechaBetweenOrderByFechaAscIdAsc(Long empresaId, LocalDate desde, LocalDate hasta);

    Optional<Saldo> findByIdAndEmpresaId(Long id, Long empresaId);

    /** Para no registrar dos veces la misma cuenta en el mismo balance. */
    List<Saldo> findByEmpresaIdAndFechaAndClasificacionAndNombreIgnoreCase(Long empresaId, LocalDate fecha,
                                                                           Clasificacion clasificacion, String nombre);

    @Query("select max(s.fecha) from Saldo s where s.empresa.id = :empresaId")
    LocalDate ultimaFecha(@Param("empresaId") Long empresaId);

    /** Fechas con balance capturado: [fecha, número de cuentas], de la más reciente a la más antigua. */
    @Query("select s.fecha, count(s) from Saldo s where s.empresa.id = :empresaId group by s.fecha order by s.fecha desc")
    List<Object[]> fechas(@Param("empresaId") Long empresaId);

    /** En cuántos balances aparece cada cuenta: [clasificación, nombre en minúsculas, total] */
    @Query("select s.clasificacion, lower(s.nombre), count(s) from Saldo s where s.empresa.id = :empresaId "
            + "group by s.clasificacion, lower(s.nombre)")
    List<Object[]> conteoPorCuenta(@Param("empresaId") Long empresaId);

    @Modifying
    @Query("delete from Saldo s where s.empresa.id = :empresaId")
    int borrarDeEmpresa(@Param("empresaId") Long empresaId);

    @Modifying
    @Query("delete from Saldo s where s.empresa.id = :empresaId and s.fecha between :desde and :hasta")
    int borrarDePeriodo(@Param("empresaId") Long empresaId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Modifying
    @Query("delete from Saldo s where s.empresa.id = :empresaId and s.fecha = :fecha")
    int borrarDeFecha(@Param("empresaId") Long empresaId, @Param("fecha") LocalDate fecha);
}
