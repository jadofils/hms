package amalitech.hospital.management.dto.doctor;

import lombok.Data;

import java.util.List;

@Data
public class DoctorResponse {
    private String doctorId;
    private String firstName;
    private String lastName;
    private String specialization;
    private String phone;
    private String email;
    /** Not populated by the paginated listing (see FindUserDataAspect's "doctor" case) — only by single-item lookups. */
    private List<DepartmentResponse> departments;
}
