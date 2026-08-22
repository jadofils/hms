package amalitech.hospital.management.service;

import amalitech.hospital.management.event.AdminCreatedUserEvent;
import amalitech.hospital.management.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercises {@link MailEventListener}'s {@code @Async}/{@code @TransactionalEventListener}
 * timing through a real Spring proxy — a plain Mockito unit test (see
 * {@link MailEventListenerTest}) can verify which {@code MailService} method each event
 * maps to, but not whether it actually waits for commit or actually hops threads, both of
 * which need the real {@code TransactionSynchronizationManager}/async-proxy machinery
 * (same "aspects/proxy-dependent behavior need a real Spring context" rule
 * {@code EmailAspectTest} follows — see {@code CLAUDE.md}'s Testing section). Only
 * {@link MailService} is mocked — {@code MailEventListener} itself, {@code AsyncConfig}'s
 * real {@code mailTaskExecutor}, and the real transactional-event-listener adapter all
 * run for real.
 */
@SpringBootTest
@ActiveProfiles("test")
class MailEventListenerTransactionalTimingTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionalPublisher transactionalPublisher;

    @MockitoBean
    private MailService mailService;

    @Test
    void listener_firesAfterCommit_onADifferentThreadThanThePublisher() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> handlerThreadName = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            handlerThreadName.set(Thread.currentThread().getName());
            latch.countDown();
            return null;
        }).when(mailService).sendEmailVerificationEmail(anyString(), anyString(), anyString(), anyInt());

        String publisherThreadName = Thread.currentThread().getName();
        transactionalPublisher.publishAndCommit();

        // Commit already happened by the time publishAndCommit() returns (its own
        // @Transactional method boundary) — the @Async handler may still be mid-flight
        // on mailTaskExecutor, hence the short bounded wait rather than an immediate assert.
        assertThat(latch.await(2, TimeUnit.SECONDS)).as("listener fired within 2s of commit").isTrue();
        verify(mailService).sendEmailVerificationEmail(
                "timing-test@example.com", "Timing", "http://localhost/verify", 24);
        assertThat(handlerThreadName.get())
                .as("listener ran off the request thread, on the named mailTaskExecutor")
                .isNotEqualTo(publisherThreadName)
                .startsWith("mail-");
    }

    @Test
    void listener_neverFires_whenThePublishingTransactionRollsBack() throws InterruptedException {
        assertThatThrownBy(transactionalPublisher::publishAndRollback)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced rollback for the test");

        // No commit ever happened, so AFTER_COMMIT must never invoke the listener at all —
        // wait a generous margin to be confident about "never", not just "not yet".
        Thread.sleep(500);
        verify(mailService, never()).sendEmailVerificationEmail(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void listener_stillFires_whenPublishedOutsideAnyTransaction_viaFallbackExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mailService).sendGeneratedPasswordEmail(anyString(), anyString(), anyString());

        // Published directly from this (non-@Transactional) test method — no active
        // transaction to defer to, exactly AuthService.forgotPassword's own situation.
        // Without fallbackExecution = true on the listener, this would silently never fire.
        eventPublisher.publishEvent(new AdminCreatedUserEvent(
                "fallback-test@example.com", "Fallback", "Gen3rat3d!"));

        assertThat(latch.await(2, TimeUnit.SECONDS))
                .as("fallbackExecution = true lets the listener fire with no active transaction")
                .isTrue();
    }

    @TestConfiguration
    static class Config {
        @Bean
        TransactionalPublisher transactionalPublisher(ApplicationEventPublisher publisher) {
            return new TransactionalPublisher(publisher);
        }
    }

    /** A real {@code @Transactional} bean the test drives directly — needed because a
     *  JUnit test method itself isn't a Spring-managed bean, so it can't be the thing
     *  {@code @Transactional} proxies. */
    static class TransactionalPublisher {
        private final ApplicationEventPublisher publisher;

        TransactionalPublisher(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishAndCommit() {
            publisher.publishEvent(new UserRegisteredEvent(
                    "timing-test@example.com", "Timing", "http://localhost/verify", 24));
        }

        @Transactional
        public void publishAndRollback() {
            publisher.publishEvent(new UserRegisteredEvent(
                    "rollback-test@example.com", "Rollback", "http://localhost/verify", 24));
            throw new RuntimeException("forced rollback for the test");
        }
    }
}
