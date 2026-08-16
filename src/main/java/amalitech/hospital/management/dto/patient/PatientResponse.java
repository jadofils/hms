package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientResponse {
    private String patientId;
    private String firstName;
    private String lastName;
    private LocalDate dob;
    private String gender;
    private String phone;
    private String email;
    private String address;
    private String status;
}
