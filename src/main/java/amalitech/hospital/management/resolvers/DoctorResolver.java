package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.DoctorRequest;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.doctor.PatchDoctorRequest;
import amalitech.hospital.management.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link DoctorService} — see {@code UserResolver}'s Javadoc for
 * the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class DoctorResolver {

    private final DoctorService doctorService;

    @QueryMapping
    public List<DoctorResponse> doctors(@Argument int page, @Argument int size, @Argument String sort) {
        return doctorService.getDoctors(GraphQlPaging.of(page, size, sort)).getContent();
    }

    @QueryMapping
    public DoctorResponse doctor(@Argument String doctorId) {
        return doctorService.getDoctor(doctorId);
    }

    @MutationMapping
    public DoctorResponse createDoctor(@Argument @Valid DoctorRequest input) {
        return doctorService.createDoctor(input);
    }

    @MutationMapping
    public DoctorResponse updateDoctor(@Argument String doctorId, @Argument @Valid DoctorRequest input) {
        return doctorService.updateDoctor(doctorId, input);
    }

    @MutationMapping
    public DoctorResponse patchDoctor(@Argument String doctorId, @Argument @Valid PatchDoctorRequest input) {
        return doctorService.patchDoctor(doctorId, input);
    }

    @MutationMapping
    public boolean deleteDoctor(@Argument String doctorId) {
        doctorService.deleteDoctor(doctorId);
        return true;
    }

    @MutationMapping
    public DoctorResponse assignDepartments(@Argument String doctorId, @Argument List<String> departmentIds) {
        doctorService.assignDepartments(doctorId, departmentIds);
        return doctorService.getDoctor(doctorId);
    }

    @MutationMapping
    public DoctorResponse removeDepartment(@Argument String doctorId, @Argument String departmentId) {
        doctorService.removeDepartment(doctorId, departmentId);
        return doctorService.getDoctor(doctorId);
    }

    /** {@code DoctorResponse.departments} is only populated by the single-item lookup,
     *  not the paginated listing (see that DTO's own Javadoc) — default to empty rather
     *  than let a null list crash against the schema's non-null {@code [Department!]!}. */
    @SchemaMapping(typeName = "Doctor", field = "departments")
    public List<DepartmentResponse> departments(DoctorResponse doctor) {
        return doctor.getDepartments() == null ? List.of() : doctor.getDepartments();
    }
}
