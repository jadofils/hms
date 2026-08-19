package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.doctor.DepartmentRequest;
import amalitech.hospital.management.dto.doctor.DoctorRequest;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@code @Transactional} rollback actually holds against a real database, not
 * just that the right exception is thrown — every other test in this codebase either
 * mocks the repository (service-layer unit tests, per CLAUDE.md's Testing section, which
 * can't observe a real rollback since there's no real transaction to roll back) or only
 * asserts on the HTTP status code (controller tests). {@code DoctorService.createDoctor}
 * is the target: its own Javadoc claims an unknown department id "rolls the whole
 * creation back rather than leaving a half-assigned doctor behind" — this is what
 * actually verifies that claim, by checking the doctor row itself was never left behind,
 * not just that the caller received a 404.
 *
 * <p>Same collision-proof unique-value approach as {@code AbstractControllerTest}/
 * {@code RestVsGraphQlBenchmarkTest} (pure integer addition, not string concatenation on
 * {@code nanoTime()} — see either class's Javadoc for why) — this hits the real shared
 * dev/test Postgres database and doesn't roll back its own successful writes afterward.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionRollbackTest {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DoctorRepository doctorRepository;

    private static final long RUN_OFFSET = System.nanoTime();
    private static final AtomicLong SEQ = new AtomicLong();

    private static String uniqueDigits(int len) {
        long value = RUN_OFFSET + SEQ.incrementAndGet();
        String digits = Long.toString(Math.abs(value));
        if (digits.length() < len) {
            digits = "0".repeat(len - digits.length()) + digits;
        }
        return digits.substring(digits.length() - len);
    }

    @Test
    void createDoctor_rollsBackTheDoctorRow_whenAnAssignDepartmentCallFailsMidTransaction() {
        DepartmentRequest departmentRequest = new DepartmentRequest();
        departmentRequest.setName("Rollback Test Dept " + uniqueDigits(6));
        String realDepartmentId = departmentService.createDepartment(departmentRequest).getDepartmentId();

        String email = "rollback-test-" + uniqueDigits(8) + "@example.com";
        DoctorRequest request = new DoctorRequest();
        request.setFirstName("Rollback");
        request.setLastName("Test");
        request.setEmail(email);
        // A real department first, then a fake one — assignDepartment succeeds for the
        // first, then throws NotFoundException for the second, midway through the same
        // @Transactional createDoctor call that already saved the doctor row itself.
        request.setDepartmentIds(List.of(realDepartmentId, "nonexistent-department-id"));

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(NotFoundException.class);

        // The real assertion: if @Transactional's rollback didn't actually hold, the
        // doctor row inserted before the failure would still be sitting in the database
        // despite the whole operation having thrown.
        assertThat(doctorRepository.existsByEmail(email)).isFalse();
    }
}
