package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial-update counterpart to {@link DepartmentRequest} — every field is optional;
 * {@code DepartmentService.patchDepartment} only touches the ones actually present in
 * the request body.
 */
@Data
public class PatchDepartmentRequest {

    @Schema(description = "Optional. New department name, up to 100 characters, letters/numbers/spaces/common "
            + "punctuation (&.,'-) only. Omit to leave the name unchanged.",
            example = "Cardiology", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9&.,' -]+$", message = "Name can only contain letters, numbers, spaces and common punctuation (&.,'-)")
    private String name;

    @Schema(description = "Optional. New free-text location, up to 150 characters. Omit to leave unchanged.",
            example = "Building A, Floor 2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 150, message = "Location must be at most 150 characters")
    private String location;

    @Schema(description = "Optional. New contact phone number: 7-15 digits, optional leading +. Omit to leave unchanged.",
            example = "+250788123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;
}
