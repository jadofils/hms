package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link DepartmentResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. */
@GraphQlTest(DepartmentResolver.class)
@Import(GraphQlConfig.class)
class DepartmentResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private DepartmentService departmentService;

    private DepartmentResponse existingDepartment() {
        DepartmentResponse response = new DepartmentResponse();
        response.setDepartmentId("dept-1");
        response.setName("Cardiology");
        return response;
    }

    @Test
    void department_returnsMappedResponse() {
        when(departmentService.getDepartment("dept-1")).thenReturn(existingDepartment());

        graphQlTester.document("{ department(departmentId: \"dept-1\") { name } }")
                .execute()
                .path("department.name").entity(String.class).isEqualTo("Cardiology");
    }

    @Test
    void department_doctors_delegatesToGetDepartmentDoctors() {
        when(departmentService.getDepartment("dept-1")).thenReturn(existingDepartment());
        DoctorResponse doctor = new DoctorResponse();
        doctor.setDoctorId("doctor-1");
        doctor.setFirstName("Greg");
        doctor.setLastName("House");
        when(departmentService.getDepartmentDoctors("dept-1")).thenReturn(List.of(doctor));

        graphQlTester.document("{ department(departmentId: \"dept-1\") { doctors { doctorId } } }")
                .execute()
                .path("department.doctors[0].doctorId").entity(String.class).isEqualTo("doctor-1");
    }

    @Test
    void createDepartment_delegatesToService() {
        when(departmentService.createDepartment(any())).thenReturn(existingDepartment());

        graphQlTester.document("mutation { createDepartment(input: { name: \"Cardiology\" }) { departmentId } }")
                .execute()
                .path("createDepartment.departmentId").entity(String.class).isEqualTo("dept-1");
    }

    @Test
    void deleteDepartment_returnsTrue() {
        graphQlTester.document("mutation { deleteDepartment(departmentId: \"dept-1\") }")
                .execute()
                .path("deleteDepartment").entity(Boolean.class).isEqualTo(true);

        verify(departmentService).deleteDepartment("dept-1");
    }
}
