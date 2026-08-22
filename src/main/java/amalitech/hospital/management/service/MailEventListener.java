package amalitech.hospital.management.service;

import amalitech.hospital.management.event.AdminCreatedUserEvent;
import amalitech.hospital.management.event.PasswordChangedEvent;
import amalitech.hospital.management.event.PasswordResetRequestedEvent;
import amalitech.hospital.management.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns each mail-triggering event into the real {@code MailService} call it replaces —
 * HMS v5, Epics 2/3. Four call sites used to call {@code mailService.xxx(...)} directly,
 * synchronously, inside (4 of 5) an open {@code @Transactional} block: holding a checked-
 * out HikariCP connection idle for the entire blocking SMTP round trip
 * ({@code EmailAspect.send} → {@code mailSender.send(...)}). Publishing an
 * {@code ApplicationEvent} instead and handling it here fixes two distinct things, not
 * one: {@code phase = AFTER_COMMIT} releases the DB connection before this listener runs
 * at all (the transaction has already committed); {@code @Async} additionally releases
 * the *request thread*, so the HTTP response doesn't wait on the SMTP round trip either.
 *
 * <p>{@code fallbackExecution = true} on every method here, uniformly — {@code
 * AuthService.forgotPassword} (the publisher of {@link PasswordResetRequestedEvent})
 * isn't {@code @Transactional} at all, so without this flag a listener with no active
 * transaction to defer to would silently never fire. Applying it to all four rather than
 * special-casing the one non-transactional publisher is simpler and has no effect when a
 * transaction genuinely is active — {@code @Async} still applies in the fallback path
 * too, since the async proxy wraps this bean itself, independent of which code path
 * (transactional-adapter callback vs. direct fallback invocation) triggers it.
 *
 * <p>Deliberately separate from the pre-existing {@code EventBus}/{@code @Subscribe}
 * mechanism ({@code NotificationEventListener} et al.) — that one is a same-transaction,
 * synchronous, DB-only, runtime-togglable pub-sub with no analogous "wait for commit,
 * then run off-thread" need. This class exists specifically because mail is different:
 * a genuinely slow, genuinely external, genuinely safe-to-defer side effect.
 */
@Service
@RequiredArgsConstructor
public class MailEventListener {

    private final MailService mailService;

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        mailService.sendEmailVerificationEmail(event.getEmail(), event.getRecipientName(),
                event.getVerifyUrl(), event.getExpiryHours());
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAdminCreatedUser(AdminCreatedUserEvent event) {
        mailService.sendGeneratedPasswordEmail(event.getEmail(), event.getRecipientName(),
                event.getGeneratedPassword());
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        mailService.sendPasswordResetEmail(event.getEmail(), event.getRecipientName(),
                event.getResetToken(), event.getResetUrl(), event.getExpiryMinutes());
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordChanged(PasswordChangedEvent event) {
        mailService.sendPasswordChangedEmail(event.getEmail(), event.getRecipientName(), event.getChangedAt());
    }
}
