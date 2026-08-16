package amalitech.hospital.management.dto.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartmentRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9&.,' -]+$", message = "Name can only contain letters, numbers, spaces and common punctuation (&.,'-)")
    private String name;

    /** Free text — no shape to validate beyond length. */
    @Size(max = 150, message = "Location must be at most 150 characters")
    private String location;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;
}
