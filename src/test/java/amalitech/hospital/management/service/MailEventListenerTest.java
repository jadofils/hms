package amalitech.hospital.management.service;

import amalitech.hospital.management.event.AdminCreatedUserEvent;
import amalitech.hospital.management.event.PasswordChangedEvent;
import amalitech.hospital.management.event.PasswordResetRequestedEvent;
import amalitech.hospital.management.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

/**
 * Plain Mockito unit test for the "which MailService method does each event map to"
 * logic — the {@code @Async}/{@code @TransactionalEventListener} timing/threading
 * behavior itself needs a real Spring proxy and is covered separately by
 * {@code MailEventListenerTransactionalTimingTest} (a {@code @SpringBootTest} slice,
 * matching {@code EmailAspectTest}'s "aspects need a real proxy" convention — see
 * {@code CLAUDE.md}'s Testing section). Same manually-constructed, framework-free shape
 * as {@code NotificationEventListenerTest}.
 */
@ExtendWith(MockitoExtension.class)
class MailEventListenerTest {

    @Mock private MailService mailService;

    private MailEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MailEventListener(mailService);
    }

    @Test
    void onUserRegistered_sendsTheVerificationEmail() {
        listener.onUserRegistered(new UserRegisteredEvent(
                "bob@example.com", "bob", "http://localhost:3000/verify-email?token=abc", 24));

        verify(mailService).sendEmailVerificationEmail(
                "bob@example.com", "bob", "http://localhost:3000/verify-email?token=abc", 24);
    }

    @Test
    void onAdminCreatedUser_sendsTheGeneratedPasswordEmail() {
        listener.onAdminCreatedUser(new AdminCreatedUserEvent("bob@example.com", "bob", "Gen3rat3d!"));

        verify(mailService).sendGeneratedPasswordEmail("bob@example.com", "bob", "Gen3rat3d!");
    }

    @Test
    void onPasswordResetRequested_sendsTheResetEmail() {
        listener.onPasswordResetRequested(new PasswordResetRequestedEvent(
                "bob@example.com", "bob", "reset-token", "http://localhost:3000/reset-password?token=reset-token", 30));

        verify(mailService).sendPasswordResetEmail(
                "bob@example.com", "bob", "reset-token",
                "http://localhost:3000/reset-password?token=reset-token", 30);
    }

    @Test
    void onPasswordChanged_sendsTheChangedConfirmationEmail() {
        LocalDateTime changedAt = LocalDateTime.now();

        listener.onPasswordChanged(new PasswordChangedEvent("bob@example.com", "bob", changedAt));

        verify(mailService).sendPasswordChangedEmail("bob@example.com", "bob", changedAt);
    }
}
