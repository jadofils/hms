package amalitech.hospital.management.repository.pharmacy;

import amalitech.hospital.management.model.pharmacy.Medication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MedicationRepository extends JpaRepository<Medication, String> {
    boolean existsByName(String name);

    /**
     * Distinct from {@code MedicalInventoryRepository.findLowStock} (which lists the
     * individual low-stock <em>batches</em>) — this answers "which medications need
     * reordering at all," one row per medication regardless of how many of its own
     * batches are low. {@code Medication} has no mapped relationship to
     * {@code MedicalInventory} (the FK only exists on the inventory side), so this is an
     * explicit cross-entity join filtered by {@code WHERE}, not something a derived
     * query name could express even with a mapped association — comparing
     * {@code quantityInStock} against {@code reorderLevel} is still two columns of the
     * joined row, and {@code DISTINCT} is required since one medication can have
     * multiple low-stock batches. Backs
     * {@code MedicationService.getMedications}' {@code lowStock} filter.
     */
    @Query("""
            SELECT DISTINCT m FROM Medication m, MedicalInventory i
            WHERE i.medication = m AND i.quantityInStock <= i.reorderLevel
              AND i.deletedAt IS NULL AND m.deletedAt IS NULL
            """)
    Page<Medication> findLowStock(Pageable pageable);
}
