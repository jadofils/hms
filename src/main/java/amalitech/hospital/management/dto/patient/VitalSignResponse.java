package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only projection of {@code VitalSign} — see {@link PatientAllergyResponse}'s
 *  Javadoc for why there's no request-DTO counterpart. */
@Data
public class VitalSignResponse {
    private String vitalId;
    private String appointmentId;
    private Short bloodPressureSystolic;
    private Short bloodPressureDiastolic;
    private Short heartRate;
    private BigDecimal temperatureCelsius;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private LocalDateTime recordedAt;
}
