package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.ScheduledMaintenance;
import amalitech.hospital.management.enums.MaintenanceInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ScheduledMaintenanceRegistrar#postProcessAfterInitialization} is a plain method
 * a {@code BeanPostProcessor} calls directly — unlike an {@code @Aspect}'s
 * {@code @Around}/{@code @Before} advice, it needs no real Spring AOP proxy to exercise
 * for real, so this is a manually-constructed Mockito unit test (see CLAUDE.md's Testing
 * section: aspects need a real proxy; this registrar isn't one).
 */
@ExtendWith(MockitoExtension.class)
class ScheduledMaintenanceRegistrarTest {

    @Mock private TaskScheduler taskScheduler;

    private ScheduledMaintenanceRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new ScheduledMaintenanceRegistrar(taskScheduler);
    }

    @Test
    void registersAnnotatedMethod_withThePeriodDerivedFromTheAnnotation() {
        TestMaintenanceBean bean = new TestMaintenanceBean();

        registrar.postProcessAfterInitialization(bean, "testMaintenanceBean");

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofDays(1)));
    }

    @Test
    void convertsHoursUnitCorrectly() {
        ThrowingMaintenanceBean bean = new ThrowingMaintenanceBean();

        registrar.postProcessAfterInitialization(bean, "throwingMaintenanceBean");

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofHours(2)));
    }

    @Test
    void returnsTheSameBeanUnchanged() {
        TestMaintenanceBean bean = new TestMaintenanceBean();

        Object result = registrar.postProcessAfterInitialization(bean, "testMaintenanceBean");

        assertThat(result).isSameAs(bean);
    }

    @Test
    void ignoresBeansWithNoAnnotatedMethods() {
        Object plainBean = new Object();

        registrar.postProcessAfterInitialization(plainBean, "plainBean");

        verify(taskScheduler, never()).scheduleAtFixedRate(any(), any(Duration.class));
    }

    @Test
    void registeredTask_invokesTheAnnotatedMethod_whenTheSchedulerRunsIt() {
        TestMaintenanceBean bean = new TestMaintenanceBean();
        registrar.postProcessAfterInitialization(bean, "testMaintenanceBean");
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), any(Duration.class));

        taskCaptor.getValue().run();

        assertThat(bean.invocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredTask_logsAndSwallows_ratherThanPropagating_whenTheAnnotatedMethodThrows() {
        ThrowingMaintenanceBean bean = new ThrowingMaintenanceBean();
        registrar.postProcessAfterInitialization(bean, "throwingMaintenanceBean");
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), any(Duration.class));

        // Must not propagate — a failing scheduled task must never kill the scheduler thread.
        taskCaptor.getValue().run();
    }

    public static class TestMaintenanceBean {
        private final AtomicInteger invocationCount = new AtomicInteger();

        @ScheduledMaintenance(value = "test-task", interval = 1, unit = MaintenanceInterval.DAYS)
        public void runTask() {
            invocationCount.incrementAndGet();
        }
    }

    public static class ThrowingMaintenanceBean {
        @ScheduledMaintenance(value = "throwing-task", interval = 2, unit = MaintenanceInterval.HOURS)
        public void runTask() {
            throw new IllegalStateException("boom");
        }
    }
}
