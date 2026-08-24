package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial-update counterpart to {@link DoctorRequest} — every field is optional;
 * {@code DoctorService.patchDoctor} only touches the ones actually present in the
 * request body. Omits {@code departmentIds} entirely — {@code DoctorRequest}'s own
 * Javadoc already documents that {@code updateDoctor} ignores it too (department
 * membership is managed by the dedicated assign/remove-department endpoints instead),
 * so there's nothing for a patch to touch there either.
 */
@Data
public class PatchDoctorRequest {

    @Schema(description = "Optional. New first name, up to 50 characters, letters/spaces/hyphens/apostrophes only. Omit to leave unchanged.",
            example = "John", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "First name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "First name can only contain letters, spaces, hyphens and apostrophes")
    private String firstName;

    @Schema(description = "Optional. New last name, up to 50 characters, letters/spaces/hyphens/apostrophes only. Omit to leave unchanged.",
            example = "Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Last name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Last name can only contain letters, spaces, hyphens and apostrophes")
    private String lastName;

    @Schema(description = "Optional. New medical specialization, up to 100 characters. Omit to leave unchanged.",
            example = "Cardiology", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Specialization must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Specialization can only contain letters, spaces, hyphens and apostrophes")
    private String specialization;

    @Schema(description = "Optional. New contact phone number: 7-15 digits, optional leading +. Omit to leave unchanged.",
            example = "+250788123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Schema(description = "Optional. New contact email address, up to 100 characters. Omit to leave unchanged.",
            example = "john.doe@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;
}
