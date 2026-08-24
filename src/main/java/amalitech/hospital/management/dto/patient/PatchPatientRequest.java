package amalitech.hospital.management.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Partial-update counterpart to {@link PatientRequest} — every field optional; only
 * the ones actually present in the request body get changed. See
 * {@code PatientService.patchPatient}.
 */
@Data
public class PatchPatientRequest {

    @Schema(description = "Optional. Patient's first name, up to 50 characters, letters/spaces/hyphens/apostrophes only.",
            example = "Jane", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "First name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "First name can only contain letters, spaces, hyphens and apostrophes")
    private String firstName;

    @Schema(description = "Optional. Patient's last name, up to 50 characters, letters/spaces/hyphens/apostrophes only.",
            example = "Smith", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Last name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Last name can only contain letters, spaces, hyphens and apostrophes")
    private String lastName;

    @Schema(description = "Optional. Patient's date of birth, cannot be in the future.",
            example = "1990-05-15", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dob;

    @Schema(description = "Optional. Patient's gender, one of: M, F, Other (case-insensitive).",
            example = "F", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(M|F|Other)$", message = "Gender must be one of: M, F, Other")
    private String gender;

    @Schema(description = "Optional. Patient's contact phone number: 7-15 digits, with an optional leading +, up to 20 characters.",
            example = "+250788123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Schema(description = "Optional. Patient's contact email address, up to 100 characters, must be a valid email format.",
            example = "jane.smith@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    @Schema(description = "Optional. Free-text home address, up to 255 characters.",
            example = "123 Main St, Kigali", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    @Schema(description = "Optional. Patient status, one of: active, inactive (case-insensitive).",
            example = "active", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(active|inactive)$", message = "Status must be one of: active, inactive")
    private String status;
}
