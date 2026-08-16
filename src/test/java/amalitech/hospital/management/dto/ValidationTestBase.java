package amalitech.hospital.management.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Set;

/**
 * Shared base for direct Bean Validation unit tests. {@code jakarta.validation.Validator}
 * needs no Spring context at all — building one directly here is far cheaper than
 * exercising the same {@code @NotBlank}/{@code @Pattern}/{@code @Past}/etc. constraints
 * indirectly through a {@code @SpringBootTest} MockMvc round trip (see
 * {@code AbstractControllerTest}), and lets every constraint on a DTO — including ones
 * no controller test happens to provoke — get its own direct, fast assertion.
 */
public abstract class ValidationTestBase {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    protected static <T> Set<ConstraintViolation<T>> validate(T target) {
        return VALIDATOR.validate(target);
    }

    /** True if validating {@code target} produces at least one violation on the exact
     *  property path {@code propertyPath} (e.g. {@code "dob"}, {@code "recipients[0]"}). */
    protected static <T> boolean hasViolationOn(T target, String propertyPath) {
        return validate(target).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(propertyPath));
    }
}
