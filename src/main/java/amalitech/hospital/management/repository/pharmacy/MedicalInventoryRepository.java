package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.MedicalInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * {@code @EntityGraph(attributePaths = "medication")} on every finder here (HMS v5) —
 * {@code MedicalInventoryService.toResponse} calls {@code inventory.getMedication()}
 * twice per row ({@code MedicalInventory}'s own {@code @ManyToOne(LAZY)} field) — 1 extra
 * `SELECT` per row without this. {@code @EntityGraph} composes fine with the custom JPQL
 * {@code @Query} below — it applies the fetch graph to that query's execution, not just
 * to derived-query methods.
 */
public interface MedicalInventoryRepository extends JpaRepository<MedicalInventory, String> {

    /** Derived query — backs {@code MedicalInventoryService.getInventoryRecords}'
     *  {@code medicationId} filter (every batch on hand for one medication). */
    @EntityGraph(attributePaths = "medication")
    Page<MedicalInventory> findByMedication_MedicationIdAndDeletedAtIsNull(String medicationId, Pageable pageable);

    /** {@code quantityInStock <= reorderLevel} compares two columns of the same row —
     *  not expressible as a derived query name, hence the custom JPQL. Backs
     *  {@code MedicalInventoryService.getInventoryRecords}' {@code lowStock} filter, a
     *  real pharmacy restock-alert worklist. */
    @Query("SELECT i FROM MedicalInventory i WHERE i.quantityInStock <= i.reorderLevel AND i.deletedAt IS NULL")
    @EntityGraph(attributePaths = "medication")
    Page<MedicalInventory> findLowStock(Pageable pageable);

    // Redeclares the inherited JpaRepository method purely to attach the same graph —
    // backs MedicalInventoryService.getInventoryRecords' unfiltered listing.
    @Override
    @EntityGraph(attributePaths = "medication")
    Page<MedicalInventory> findAll(Pageable pageable);

    // Same graph on the single-item lookup — MedicalInventoryService.getInventoryRecord's
    // toResponse walks the identical .getMedication() chain for that one row. Only
    // surfaced once spring.jpa.open-in-view was disabled (HMS v5) — previously masked
    // by OSIV keeping a session open for the whole request.
    @Override
    @EntityGraph(attributePaths = "medication")
    Optional<MedicalInventory> findById(String inventoryId);
}
