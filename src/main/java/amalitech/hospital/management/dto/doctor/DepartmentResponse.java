package amalitech.hospital.management.dto.doctor;

import lombok.Data;

import java.util.List;

@Data
public class DepartmentResponse {
    private String departmentId;
    private String name;
    private String location;
    private String phone;
    /** Not populated by the paginated listing or by create/update — only by the
     *  single-item lookup ({@code DepartmentService.getDepartment}), same convention as
     *  {@code DoctorResponse.departments}. */
    private List<DoctorResponse> doctors;
}
