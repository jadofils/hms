package amalitech.hospital.management.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientRequest {

    @Schema(description = "Patient's first name. Required, up to 50 characters, letters/spaces/hyphens/apostrophes only.", example = "Jane", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "First name can only contain letters, spaces, hyphens and apostrophes")
    private String firstName;

    @Schema(description = "Patient's last name. Required, up to 50 characters, letters/spaces/hyphens/apostrophes only.", example = "Smith", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Last name can only contain letters, spaces, hyphens and apostrophes")
    private String lastName;

    // PastOrPresent, not Past — a patient can be registered on their own birth day
    // (dob == today), and LocalDate has no time component to disambiguate a same-day
    // birth as "earlier today" vs. "later today" anyway.
    @Schema(description = "Patient's date of birth. Required, and cannot be in the future.", example = "1990-05-15", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Date of birth is required")
    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dob;

    @Schema(description = "Patient's gender. Required, one of: M, F, Other (case-insensitive).", example = "F", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Gender is required")
    @Pattern(regexp = "(?i)^(M|F|Other)$", message = "Gender must be one of: M, F, Other")
    private String gender;

    @Schema(description = "Optional. Patient's contact phone number: 7-15 digits, with an optional leading +, up to 20 characters.", example = "+250788123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Schema(description = "Optional. Patient's contact email address, up to 100 characters, must be a valid email format.", example = "jane.smith@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    /** Free text — no shape to validate beyond length. */
    @Schema(description = "Optional. Free-text home address, up to 255 characters.", example = "123 Main St, Kigali", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    /** Optional on create — the entity defaults to "active". */
    @Schema(description = "Optional. Patient status, one of: active, inactive (case-insensitive). Defaults to \"active\" when omitted.", example = "active", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(active|inactive)$", message = "Status must be one of: active, inactive")
    private String status;
}
