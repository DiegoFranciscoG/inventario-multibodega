package io.github.diegofranciscog.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.Lot;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByProductIdAndLotNumber(Long productId, String lotNumber);

    List<Lot> findByProductIdOrderByExpiryDateAsc(Long productId);

    /**
     * Crea el lote si no existe. {@code ON CONFLICT DO NOTHING} evita la carrera entre dos ingresos simultáneos del
     * mismo lote: el segundo espera al primero y no falla.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into lot (product_id, lot_number, expiry_date, production_date)
            values (:productId, :lotNumber, :expiryDate, :productionDate)
            on conflict (product_id, lot_number) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("productId") Long productId, @Param("lotNumber") String lotNumber,
                       @Param("expiryDate") LocalDate expiryDate, @Param("productionDate") LocalDate productionDate);
}
