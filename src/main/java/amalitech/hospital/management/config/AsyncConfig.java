package amalitech.hospital.management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code @EnableAsync} + the two named {@code ThreadPoolTaskExecutor} beans every
 * {@code @Async} method in this codebase uses (HMS v5). Deliberately not
 * {@link SchedulingConfig}'s {@code ThreadPoolTaskScheduler} — that bean is a
 * {@code TaskScheduler} (cron/fixed-rate dispatch for the project's own
 * {@code @ScheduledMaintenance} mechanism), a different Spring interface entirely from
 * the {@code Executor} {@code @Async} needs, and sized only for that unrelated,
 * low-frequency workload.
 *
 * <p><b>Always name one of these two explicitly on every {@code @Async}</b> —
 * {@code @Async("mailTaskExecutor")}/{@code @Async("patientProfileExecutor")}, never a
 * bare {@code @Async}. Once {@code @EnableAsync} is on the classpath, a bare
 * {@code @Async} with no qualifier silently falls back to Spring's own
 * {@code SimpleAsyncTaskExecutor} — unbounded, unpooled, a brand-new OS thread per
 * invocation, no backpressure at all. That failure mode produces no error and no log
 * line; it's invisible until it isn't, under real concurrent load.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Backs {@code MailEventListener}'s four {@code @TransactionalEventListener} methods
     * — small and I/O-bound (blocking SMTP sends via {@code EmailAspect}), never
     * CPU-heavy, so a small pool is correct: a burst of registrations doesn't need many
     * concurrent threads, it needs the request thread freed immediately, which
     * {@code @Async} already does regardless of pool size.
     */
    @Bean
    public ThreadPoolTaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }

    /**
     * Backs {@code PatientService.getPatient}'s {@code CompletableFuture} fan-out (8
     * independent, small reads per request) — sized a little larger than
     * {@link #mailTaskExecutor()} since each {@code getPatient} call briefly checks out
     * several {@code patientProfileExecutor} threads at once (see that method's own
     * Javadoc), not one at a time like a mail send.
     */
    @Bean
    public ThreadPoolTaskExecutor patientProfileExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("patient-profile-");
        executor.initialize();
        return executor;
    }
}
