package mx.ipn.finanzas.repository;

import mx.ipn.finanzas.model.Clasificacion;
import mx.ipn.finanzas.model.CuentaCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaCatalogoRepository extends JpaRepository<CuentaCatalogo, Long> {

    List<CuentaCatalogo> findByEmpresaIdOrderByOrdenAscIdAsc(Long empresaId);

    Optional<CuentaCatalogo> findByIdAndEmpresaId(Long id, Long empresaId);

    Optional<CuentaCatalogo> findFirstByEmpresaIdAndClasificacionAndNombreIgnoreCase(Long empresaId,
                                                                                     Clasificacion clasificacion,
                                                                                     String nombre);

    @Query("select coalesce(max(c.orden), 0) from CuentaCatalogo c where c.empresa.id = :empresaId")
    int maxOrden(@Param("empresaId") Long empresaId);

    @Modifying
    @Query("delete from CuentaCatalogo c where c.empresa.id = :empresaId")
    int borrarDeEmpresa(@Param("empresaId") Long empresaId);
}
