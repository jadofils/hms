package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link DoctorResolver} — see {@code UserResolverTest}'s Javadoc for the
 *  shared reasoning. */
@GraphQlTest(DoctorResolver.class)
@Import(GraphQlConfig.class)
class DoctorResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private DoctorService doctorService;

    private DoctorResponse existingDoctor() {
        DoctorResponse response = new DoctorResponse();
        response.setDoctorId("doctor-1");
        response.setFirstName("Greg");
        response.setLastName("House");
        response.setDepartments(null);
        return response;
    }

    @Test
    void doctor_returnsMappedResponse() {
        when(doctorService.getDoctor("doctor-1")).thenReturn(existingDoctor());

        graphQlTester.document("{ doctor(doctorId: \"doctor-1\") { firstName lastName departments { departmentId } } }")
                .execute()
                .path("doctor.firstName").entity(String.class).isEqualTo("Greg")
                .path("doctor.departments").entityList(Object.class).hasSize(0);

        verify(doctorService).getDoctor("doctor-1");
    }

    @Test
    void createDoctor_delegatesToService() {
        when(doctorService.createDoctor(any())).thenReturn(existingDoctor());

        graphQlTester.document("mutation { createDoctor(input: { firstName: \"Greg\", lastName: \"House\" }) { doctorId } }")
                .execute()
                .path("createDoctor.doctorId").entity(String.class).isEqualTo("doctor-1");

        verify(doctorService).createDoctor(any());
    }

    @Test
    void assignDepartment_callsServiceThenReturnsRefreshedDoctor() {
        when(doctorService.getDoctor("doctor-1")).thenReturn(existingDoctor());

        graphQlTester.document("mutation { assignDepartment(doctorId: \"doctor-1\", departmentId: \"dept-1\") { doctorId } }")
                .execute()
                .path("assignDepartment.doctorId").entity(String.class).isEqualTo("doctor-1");

        verify(doctorService).assignDepartment("doctor-1", "dept-1");
    }

    @Test
    void deleteDoctor_returnsTrue() {
        graphQlTester.document("mutation { deleteDoctor(doctorId: \"doctor-1\") }")
                .execute()
                .path("deleteDoctor").entity(Boolean.class).isEqualTo(true);

        verify(doctorService).deleteDoctor("doctor-1");
    }
}
