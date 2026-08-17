package amalitech.hospital.management.dto.pharmacy;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PrescriptionResponse {
    private String prescriptionId;
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private LocalDate dateIssued;
    /** Not populated by the paginated listing or by create/update — only by the
     *  single-item lookup ({@code PrescriptionService.getPrescription}), same
     *  convention as {@code DoctorResponse.departments}. */
    private List<PrescriptionItemResponse> items;
}
