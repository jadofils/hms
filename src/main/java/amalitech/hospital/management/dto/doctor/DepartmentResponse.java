package amalitech.hospital.management.dto.doctor;

import lombok.Data;

@Data
public class DepartmentResponse {
    private String departmentId;
    private String name;
    private String location;
    private String phone;
}
