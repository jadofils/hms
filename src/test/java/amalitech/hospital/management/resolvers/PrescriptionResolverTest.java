package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.dto.pharmacy.PrescriptionResponse;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.PrescriptionItemService;
import amalitech.hospital.management.service.PrescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link PrescriptionResolver} — see {@code UserResolverTest}'s Javadoc
 *  for the shared reasoning. */
@GraphQlTest(PrescriptionResolver.class)
@Import(GraphQlConfig.class)
class PrescriptionResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private PrescriptionService prescriptionService;
    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private PrescriptionItemService prescriptionItemService;

    private PrescriptionResponse existingPrescription() {
        PrescriptionResponse response = new PrescriptionResponse();
        response.setPrescriptionId("presc-1");
        response.setAppointmentId("appt-1");
        response.setDateIssued(LocalDate.of(2026, 1, 1));
        return response;
    }

    @Test
    void prescription_returnsRealAppointmentAndItems() {
        when(prescriptionService.getPrescription("presc-1")).thenReturn(existingPrescription());
        AppointmentResponse appointment = new AppointmentResponse();
        appointment.setAppointmentId("appt-1");
        appointment.setStatus("completed");
        when(appointmentService.getAppointment("appt-1")).thenReturn(appointment);
        PrescriptionItemResponse item = new PrescriptionItemResponse();
        item.setItemId("item-1");
        item.setMedicationId("med-1");
        item.setQuantity(2);
        when(prescriptionItemService.getItems("presc-1")).thenReturn(List.of(item));

        graphQlTester.document(
                        "{ prescription(prescriptionId: \"presc-1\") { dateIssued appointment { status } items { itemId quantity } } }")
                .execute()
                .path("prescription.dateIssued").entity(String.class).isEqualTo("2026-01-01")
                .path("prescription.appointment.status").entity(String.class).isEqualTo("completed")
                .path("prescription.items[0].quantity").entity(Integer.class).isEqualTo(2);
    }

    @Test
    void createPrescription_delegatesToService() {
        when(prescriptionService.createPrescription(any())).thenReturn(existingPrescription());

        graphQlTester.document("mutation { createPrescription(input: { appointmentId: \"appt-1\" }) { prescriptionId } }")
                .execute()
                .path("createPrescription.prescriptionId").entity(String.class).isEqualTo("presc-1");
    }

    @Test
    void deletePrescription_returnsTrue() {
        graphQlTester.document("mutation { deletePrescription(prescriptionId: \"presc-1\") }")
                .execute()
                .path("deletePrescription").entity(Boolean.class).isEqualTo(true);

        verify(prescriptionService).deletePrescription("presc-1");
    }
}
