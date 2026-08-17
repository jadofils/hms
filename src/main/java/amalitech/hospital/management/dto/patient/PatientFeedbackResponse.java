package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDate;

/**
 * Read-only projection of {@code PatientFeedback} — see {@link PatientAllergyResponse}'s
 * Javadoc for why there's no request-DTO counterpart. {@code submittedBy} stays a raw
 * string here (not flattened to a username) since the entity's own field is still a
 * plain column pending a real {@code User} FK (see the entity's own comment).
 */
@Data
public class PatientFeedbackResponse {
    private String feedbackId;
    private String appointmentId;
    private String submittedBy;
    private Short rating;
    private String comments;
    private LocalDate dateSubmitted;
}
