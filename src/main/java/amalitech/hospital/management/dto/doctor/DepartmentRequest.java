package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartmentRequest {

    @Schema(description = "Department name. Required, up to 100 characters, and may only contain letters, numbers, spaces and common punctuation (&.,'-).", example = "Cardiology", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9&.,' -]+$", message = "Name can only contain letters, numbers, spaces and common punctuation (&.,'-)")
    private String name;

    /** Free text — no shape to validate beyond length. */
    @Schema(description = "Optional. Free-text location of the department, up to 150 characters.", example = "Building A, Floor 2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 150, message = "Location must be at most 150 characters")
    private String location;

    @Schema(description = "Optional. Department contact phone number: 7-15 digits, with an optional leading +, up to 20 characters.", example = "+250788123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;
}
