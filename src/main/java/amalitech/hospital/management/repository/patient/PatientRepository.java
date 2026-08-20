package amalitech.hospital.management.repository.patient;

import amalitech.hospital.management.model.patient.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, String> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /**
     * {@code nativeQuery = true} — genuinely native, not the {@code QueryBuilder}/
     * {@code EntityManager.createNativeQuery} mechanism {@code FindUserDataAspect}
     * uses for {@code getPatients}' own default listing. Postgres' {@code AGE()}
     * function has no portable JPQL equivalent (JPQL's function set is intentionally
     * database-agnostic, so it has no date-of-birth-to-age computation at all) — this is
     * a real case where reaching for native SQL over JPQL is the only option, not a
     * stylistic choice. {@code EXTRACT(YEAR FROM AGE(...))} rather than a simpler
     * {@code CURRENT_DATE - dob > (:age * 365)} for the same reason real "years old"
     * logic always accounts for leap years rather than approximating with a flat
     * 365-day year. A native query returning the mapped entity type still needs an
     * explicit {@code countQuery} for {@code Page<T>} — Spring Data can't always derive
     * one from an arbitrary native SQL string the way it can from JPQL. Backs
     * {@code PatientService.getPatients}' {@code minAge} filter.
     */
    @Query(value = """
            SELECT * FROM patients
            WHERE EXTRACT(YEAR FROM AGE(CURRENT_DATE, dob)) >= :age AND deleted_at IS NULL
            """,
            countQuery = """
            SELECT COUNT(*) FROM patients
            WHERE EXTRACT(YEAR FROM AGE(CURRENT_DATE, dob)) >= :age AND deleted_at IS NULL
            """,
            nativeQuery = true)
    Page<Patient> findByMinAgeNative(@Param("age") int age, Pageable pageable);
}
