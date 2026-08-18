package amalitech.hospital.management.aop;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.resolvers.DoctorResolver;
import amalitech.hospital.management.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Slice test for {@link GraphQlExceptionResolver} — exercised through a real resolver
 * ({@link DoctorResolver}) rather than by hand-building a {@code DataFetchingEnvironment}
 * (which {@code GraphqlErrorBuilder.newError(env)} needs a real one of to populate
 * path/location), the same "needs a real Spring GraphQL pipeline, not a plain Mockito
 * unit test" reasoning {@code CLAUDE.md}'s Testing section gives for AOP aspects needing
 * a real proxy. {@code GraphQlExceptionResolver} is a plain {@code @Component}, not a
 * {@code @Controller}, so unlike {@code DoctorResolver} it isn't picked up by
 * {@code @GraphQlTest}'s own component scan and needs its own {@code @Import}.
 */
@GraphQlTest(DoctorResolver.class)
@Import({GraphQlConfig.class, GraphQlExceptionResolver.class})
class GraphQlExceptionResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private DoctorService doctorService;

    private static final String VALID_CREATE_DOCTOR =
            "mutation { createDoctor(input: { firstName: \"John\", lastName: \"Doe\" }) { doctorId } }";

    @Test
    void notFoundException_mapsToNotFoundClassification_withTheRealMessage() {
        when(doctorService.createDoctor(any())).thenThrow(new NotFoundException("Department not found: dept-1"));

        graphQlTester.document(VALID_CREATE_DOCTOR).execute()
                .errors().satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getMessage()).isEqualTo("Department not found: dept-1");
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 404);
                    assertThat(errors.get(0).getExtensions()).containsEntry("error", "Not Found");
                    assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                });
    }

    @Test
    void badRequestException_mapsToBadRequestClassification() {
        when(doctorService.createDoctor(any()))
                .thenThrow(new BadRequestException("A doctor must be assigned to at least one department"));

        graphQlTester.document(VALID_CREATE_DOCTOR).execute()
                .errors().satisfy(errors -> {
                    assertThat(errors.get(0).getMessage())
                            .isEqualTo("A doctor must be assigned to at least one department");
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 400);
                    assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    @Test
    void conflictException_mapsToConflictStatus_underTheBadRequestClassification() {
        when(doctorService.createDoctor(any()))
                .thenThrow(new ConflictException("Email 'john@example.com' is already registered"));

        graphQlTester.document(VALID_CREATE_DOCTOR).execute()
                .errors().satisfy(errors -> {
                    assertThat(errors.get(0).getMessage())
                            .isEqualTo("Email 'john@example.com' is already registered");
                    // GraphQL's ErrorType has no dedicated CONFLICT value — the real HTTP
                    // status still rides in extensions.status even though the
                    // classification itself falls under BAD_REQUEST.
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 409);
                    assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    @Test
    void beanValidationFailure_mapsToBadRequest_withFieldLevelMessages() {
        graphQlTester.document(
                        "mutation { createDoctor(input: { firstName: \"123\", lastName: \"Doe\" }) { doctorId } }")
                .execute()
                .errors().satisfy(errors -> {
                    assertThat(errors.get(0).getMessage()).contains("firstName");
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 400);
                    assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    @Test
    void dataIntegrityViolation_mapsToConflictStatus_withoutLeakingTheRawDbMessage() {
        when(doctorService.createDoctor(any())).thenThrow(new DataIntegrityViolationException("pk violation on doctors"));

        graphQlTester.document(VALID_CREATE_DOCTOR).execute()
                .errors().satisfy(errors -> {
                    assertThat(errors.get(0).getMessage()).isEqualTo("The request conflicts with existing data");
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 409);
                });
    }

    @Test
    void unmappedException_mapsToInternalError_withAGenericMessage_notTheRawExceptionText() {
        when(doctorService.createDoctor(any())).thenThrow(new IllegalStateException("some internal detail"));

        graphQlTester.document(VALID_CREATE_DOCTOR).execute()
                .errors().satisfy(errors -> {
                    assertThat(errors.get(0).getMessage()).isEqualTo("An unexpected error occurred");
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 500);
                    assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
                });
    }

    @Test
    void happyPath_stillReturnsData_whenNothingThrows() {
        DoctorResponse response = new DoctorResponse();
        response.setDoctorId("doctor-1");
        when(doctorService.createDoctor(any())).thenReturn(response);

        graphQlTester.document(VALID_CREATE_DOCTOR).execute()
                .path("createDoctor.doctorId").entity(String.class).isEqualTo("doctor-1");
    }
}
