package com.medcore.inventory.repository;

import com.medcore.inventory.domain.entity.Reagent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReagentRepository extends JpaRepository<Reagent, Long> {

    Optional<Reagent> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    Page<Reagent> findByActivoTrueOrderByCategoriaAscNombreAsc(Pageable pageable);

    List<Reagent> findByCategoriaAndActivoTrueOrderByNombreAsc(String categoria);

    @Query("""
           SELECT r FROM Reagent r
           WHERE r.activo = TRUE AND LOWER(r.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
           ORDER BY r.nombre
           """)
    List<Reagent> buscarPorNombre(@Param("texto") String texto);

    @Query("SELECT DISTINCT r.categoria FROM Reagent r WHERE r.activo = TRUE ORDER BY r.categoria")
    List<String> categorias();

    /**
     * Reactivos cuyo total disponible esta por debajo de su minimo.
     *
     * <p>Suma solo lo que se puede usar: descarta los lotes retirados y los
     * vencidos. Un almacen lleno de material caducado no es stock.
     *
     * <p>El LEFT JOIN es necesario para que aparezcan tambien los reactivos
     * sin ningun lote: ahi el total es cero y estan bajo minimo, que es
     * precisamente el caso mas urgente. Con un JOIN normal quedarian fuera.
     */
    @Query("""
           SELECT r FROM Reagent r
           WHERE r.activo = TRUE
             AND r.stockMinimo > 0
             AND (
               SELECT COALESCE(SUM(s.cantidadActual), 0) FROM Stock s
               WHERE s.reactivo = r
                 AND s.retirado = FALSE
                 AND (s.fechaVencimiento IS NULL OR s.fechaVencimiento >= CURRENT_DATE)
             ) < r.stockMinimo
           ORDER BY r.categoria, r.nombre
           """)
    List<Reagent> reactivosBajoMinimo();

    long countByActivoTrue();
}
