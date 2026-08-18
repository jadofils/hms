package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * One row of {@code DepartmentController}'s {@code GET /api/v1/departments/staffing-summary}
 * — a department's id/name plus how many active doctors are currently assigned to it.
 * Only departments with at least one doctor appear (see
 * {@code SqlQueryBuilderAspect}'s {@code "findDepartmentsWithDoctors"} case's
 * {@code HAVING COUNT(...) > 0}) — an unstaffed department is a gap to notice on
 * {@code GET /api/v1/departments}, not something this summary buries among staffed ones.
 */
@Data
public class DepartmentDoctorCountResponse {

    @Schema(description = "Department UUID.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String departmentId;

    @Schema(description = "Department name.", example = "Cardiology")
    private String name;

    @Schema(description = "Number of active doctors currently assigned to this department.", example = "4")
    private long doctorCount;
}
