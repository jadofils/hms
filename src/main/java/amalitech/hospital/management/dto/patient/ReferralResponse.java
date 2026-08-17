package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDateTime;

/** Read-only projection of {@code Referral} — see {@link PatientAllergyResponse}'s
 *  Javadoc for why there's no request-DTO counterpart. */
@Data
public class ReferralResponse {
    private String referralId;
    private String appointmentId;
    private String referringDoctorId;
    private String referringDoctorName;
    private String referredToDoctorId;
    private String referredToDoctorName;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
}
