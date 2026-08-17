package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Read-only projection of {@code PatientAllergy} — this entity has no
 * repository/service/controller layer of its own (see {@code CLAUDE.md}), so this DTO
 * exists solely to expose it as part of {@link PatientResponse#getAllergies()}; there is
 * no corresponding request DTO since nothing here is independently creatable/updatable
 * through the API yet.
 */
@Data
public class PatientAllergyResponse {
    private String allergyId;
    private String allergen;
    private String reaction;
    private String severity;
    private LocalDateTime createdAt;
}
