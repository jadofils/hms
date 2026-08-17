package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.doctor.DepartmentRequest;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link DepartmentService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class DepartmentResolver {

    private final DepartmentService departmentService;

    @QueryMapping
    public List<DepartmentResponse> departments(@Argument int page, @Argument int size) {
        return departmentService.getDepartments(PageRequest.of(page, size)).getContent();
    }

    @QueryMapping
    public DepartmentResponse department(@Argument String departmentId) {
        return departmentService.getDepartment(departmentId);
    }

    @MutationMapping
    public DepartmentResponse createDepartment(@Argument @Valid DepartmentRequest input) {
        return departmentService.createDepartment(input);
    }

    @MutationMapping
    public DepartmentResponse updateDepartment(@Argument String departmentId, @Argument @Valid DepartmentRequest input) {
        return departmentService.updateDepartment(departmentId, input);
    }

    @MutationMapping
    public boolean deleteDepartment(@Argument String departmentId) {
        departmentService.deleteDepartment(departmentId);
        return true;
    }

    @SchemaMapping(typeName = "Department", field = "doctors")
    public List<DoctorResponse> doctors(DepartmentResponse department) {
        return departmentService.getDepartmentDoctors(department.getDepartmentId());
    }
}
