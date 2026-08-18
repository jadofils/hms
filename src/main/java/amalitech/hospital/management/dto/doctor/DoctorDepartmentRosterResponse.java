package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * One row of {@code DoctorController}'s {@code GET /api/v1/doctors/roster} — a doctor
 * paired with one department they belong to. A doctor in more than one department
 * appears once per department (Doctor&lt;-&gt;Department is many-to-many), unlike
 * {@code DoctorResponse}, which nests every department under one doctor.
 */
@Data
public class DoctorDepartmentRosterResponse {

    @Schema(description = "Doctor UUID.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String doctorId;

    @Schema(description = "Doctor's first name.", example = "Greg")
    private String firstName;

    @Schema(description = "Doctor's last name.", example = "House")
    private String lastName;

    @Schema(description = "Name of the department this row pairs the doctor with.", example = "Diagnostics")
    private String department;
}
