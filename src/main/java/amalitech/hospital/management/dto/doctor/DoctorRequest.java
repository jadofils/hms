package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DoctorRequest {

    @Schema(description = "Doctor's first name. Required, up to 50 characters, letters/spaces/hyphens/apostrophes only.", example = "John", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "First name can only contain letters, spaces, hyphens and apostrophes")
    private String firstName;

    @Schema(description = "Doctor's last name. Required, up to 50 characters, letters/spaces/hyphens/apostrophes only.", example = "Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Last name can only contain letters, spaces, hyphens and apostrophes")
    private String lastName;

    /** Optional. */
    @Schema(description = "Optional. Medical specialization, up to 100 characters, letters/spaces/hyphens/apostrophes only.", example = "Cardiology", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Specialization must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Specialization can only contain letters, spaces, hyphens and apostrophes")
    private String specialization;

    @Schema(description = "Optional. Doctor's contact phone number: 7-15 digits, with an optional leading +, up to 20 characters.", example = "+250788123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Schema(description = "Optional. Doctor's contact email address, up to 100 characters, must be a valid email format.", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    /**
     * Not {@code @NotEmpty} here on purpose: this same DTO also backs
     * {@code PUT /api/v1/doctors/{id}}, which never touches department membership
     * (that's managed by the dedicated assign/remove endpoints — see
     * {@code DoctorService.assignDepartment}/{@code removeDepartment}), so a bean-level
     * required-ness would wrongly force every update request to resend it too.
     * {@code DoctorService.createDoctor} enforces "at least one" itself, since that rule
     * only applies to creation — every doctor must belong to somewhere, but only *when
     * first created*; see {@code DoctorService}'s Javadoc.
     */
    @Schema(description = "Optional at the DTO level (ignored entirely by update — department membership is managed by the separate assign/remove department endpoints instead). Required to contain at least one existing department id when creating a new doctor — DoctorService.createDoctor rejects an empty/missing list.", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@NotBlank(message = "Department id cannot be blank") String> departmentIds;
}
