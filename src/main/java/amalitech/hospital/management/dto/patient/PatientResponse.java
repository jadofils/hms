package amalitech.hospital.management.dto.patient;

import amalitech.hospital.management.dto.finance.InvoiceResponse;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * Not populated by the paginated listing or by create/update — only by the
     * single-item lookup ({@code PatientService.getPatient}), same convention as
     * {@code DoctorResponse.departments}/{@code UserResponse.roles}. These eight fields
     * cover every piece of data in the schema that's actually linked to a patient,
     * including the six sub-entities ({@code MedicalRecord}, {@code PatientAllergy},
     * {@code PatientFeedback}, {@code PatientNote}, {@code VitalSign}, {@code Referral})
     * that otherwise have no repository/service/controller layer of their own (see
     * {@code CLAUDE.md}) — a full patient profile would be missing real clinical data
     * without them, even though nothing here is independently creatable/updatable
     * through the API yet.
     */
    private List<AppointmentResponse> appointments;
    private List<InvoiceResponse> invoices;
    private List<PatientAllergyResponse> allergies;
    private List<PatientFeedbackResponse> feedback;
    private List<PatientNoteResponse> notes;
    private List<MedicalRecordResponse> medicalRecords;
    private List<VitalSignResponse> vitalSigns;
    private List<ReferralResponse> referrals;
}
