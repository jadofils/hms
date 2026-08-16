package amalitech.hospital.management.aop;

import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.logs.SystemLog;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import amalitech.hospital.management.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link LoggingAspect}'s failure-persistence branch through the real Spring
 * AOP proxy — a plain Mockito unit test can't verify {@code @Around} interception (see
 * CLAUDE.md's Testing section). {@code PatientService.getPatient} is just a convenient,
 * already-real service method to provoke a {@code NotFoundException} through; nothing
 * about this test is specific to patients — {@code LoggingAspect}'s pointcut covers the
 * entire service layer.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoggingAspectTest {

    @Autowired
    private PatientService patientService;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Test
    void serviceFailure_persistsASystemLogRow() {
        String uniqueId = "nonexistent-log-test-" + System.nanoTime();

        assertThatThrownBy(() -> patientService.getPatient(uniqueId))
                .isInstanceOf(NotFoundException.class);

        List<SystemLog> logs = systemLogRepository.findAll();
        assertThat(logs).anyMatch(entry ->
                "ERROR".equals(entry.getLogLevel())
                        && entry.getSource() != null && entry.getSource().contains("PatientService")
                        && entry.getMessage() != null && entry.getMessage().contains(uniqueId));
    }
}
