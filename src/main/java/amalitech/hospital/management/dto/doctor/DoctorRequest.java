package amalitech.hospital.management.dto.doctor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DoctorRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "First name can only contain letters, spaces, hyphens and apostrophes")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Last name can only contain letters, spaces, hyphens and apostrophes")
    private String lastName;

    /** Optional. */
    @Size(max = 100, message = "Specialization must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z' -]+$", message = "Specialization can only contain letters, spaces, hyphens and apostrophes")
    private String specialization;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, with an optional leading +")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;
}
