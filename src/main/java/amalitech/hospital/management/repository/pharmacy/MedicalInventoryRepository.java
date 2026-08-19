package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.MedicalInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MedicalInventoryRepository extends JpaRepository<MedicalInventory, String> {
    /** Derived query — backs {@code MedicalInventoryService.getInventoryRecords}'
     *  {@code medicationId} filter (every batch on hand for one medication). */
    Page<MedicalInventory> findByMedication_MedicationIdAndDeletedAtIsNull(String medicationId, Pageable pageable);

    /** {@code quantityInStock <= reorderLevel} compares two columns of the same row —
     *  not expressible as a derived query name, hence the custom JPQL. Backs
     *  {@code MedicalInventoryService.getInventoryRecords}' {@code lowStock} filter, a
     *  real pharmacy restock-alert worklist. */
    @Query("SELECT i FROM MedicalInventory i WHERE i.quantityInStock <= i.reorderLevel AND i.deletedAt IS NULL")
    Page<MedicalInventory> findLowStock(Pageable pageable);
}
