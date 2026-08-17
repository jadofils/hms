package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.lab.LabOrderResponse;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.DoctorService;
import amalitech.hospital.management.service.LabOrderService;
import amalitech.hospital.management.service.LabResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link LabOrderResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. */
@GraphQlTest(LabOrderResolver.class)
@Import(GraphQlConfig.class)
class LabOrderResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private LabOrderService labOrderService;
    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private DoctorService doctorService;
    @MockitoBean
    private LabResultService labResultService;

    private LabOrderResponse existingLabOrder() {
        LabOrderResponse response = new LabOrderResponse();
        response.setLabOrderId("lab-1");
        response.setAppointmentId("appt-1");
        response.setDoctorId("doctor-1");
        response.setTestName("Blood Panel");
        response.setStatus("ordered");
        response.setOrderedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return response;
    }

    @Test
    void labOrder_returnsNullResult_whenNoneRecordedYet() {
        when(labOrderService.getLabOrder("lab-1")).thenReturn(existingLabOrder());
        when(appointmentService.getAppointment("appt-1")).thenReturn(new AppointmentResponse());
        when(doctorService.getDoctor("doctor-1")).thenReturn(new DoctorResponse());
        when(labResultService.getResult("lab-1")).thenThrow(new NotFoundException("Result not found"));

        graphQlTester.document("{ labOrder(labOrderId: \"lab-1\") { testName result { labResultId } } }")
                .execute()
                .path("labOrder.testName").entity(String.class).isEqualTo("Blood Panel")
                .path("labOrder.result").valueIsNull();
    }

    @Test
    void labOrder_returnsResult_whenRecorded() {
        when(labOrderService.getLabOrder("lab-1")).thenReturn(existingLabOrder());
        when(appointmentService.getAppointment("appt-1")).thenReturn(new AppointmentResponse());
        when(doctorService.getDoctor("doctor-1")).thenReturn(new DoctorResponse());
        LabResultResponse result = new LabResultResponse();
        result.setLabResultId("result-1");
        result.setIsAbnormal(false);
        when(labResultService.getResult("lab-1")).thenReturn(result);

        graphQlTester.document("{ labOrder(labOrderId: \"lab-1\") { result { labResultId } } }")
                .execute()
                .path("labOrder.result.labResultId").entity(String.class).isEqualTo("result-1");
    }

    @Test
    void createLabOrder_delegatesToService() {
        when(labOrderService.createLabOrder(any())).thenReturn(existingLabOrder());

        graphQlTester.document(
                        "mutation { createLabOrder(input: { appointmentId: \"appt-1\", doctorId: \"doctor-1\", testName: \"Blood Panel\" }) { labOrderId } }")
                .execute()
                .path("createLabOrder.labOrderId").entity(String.class).isEqualTo("lab-1");
    }

    @Test
    void deleteLabOrder_returnsTrue() {
        graphQlTester.document("mutation { deleteLabOrder(labOrderId: \"lab-1\") }")
                .execute()
                .path("deleteLabOrder").entity(Boolean.class).isEqualTo(true);

        verify(labOrderService).deleteLabOrder("lab-1");
    }
}
