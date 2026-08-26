package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Bulk counterpart to {@code POST /api/v1/doctors/{doctorId}/departments/{departmentId}}
 * — a doctor can belong to many departments at once, and a department can hold many
 * doctors (see {@code DoctorService}'s own Javadoc), so this grants every id in one
 * call instead of one request per department. See
 * {@code DoctorService.assignDepartments} for the atomicity this implies: if any single
 * id doesn't exist or the doctor is already assigned to it, the whole call fails and
 * nothing in the list is assigned — the same all-or-nothing behavior
 * {@code DoctorRequest.departmentIds} already has at doctor-creation time.
 */
@Data
public class AssignDepartmentsRequest {

    @Schema(description = "IDs of every department to assign the doctor to. Required, at least one.",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "At least one department id is required")
    private List<@NotBlank(message = "Department id cannot be blank") String> departmentIds;
}
