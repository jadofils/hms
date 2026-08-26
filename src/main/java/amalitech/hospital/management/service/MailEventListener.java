package amalitech.hospital.management.service;

import amalitech.hospital.management.event.AdminCreatedUserEvent;
import amalitech.hospital.management.event.PasswordChangedEvent;
import amalitech.hospital.management.event.PasswordResetRequestedEvent;
import amalitech.hospital.management.event.UserInvitedEvent;
import amalitech.hospital.management.event.UserRegisteredEvent;
import amalitech.hospital.management.event.UserRoleMissingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    // Only used to build onUserRoleMissing's CTA link — same deep-link-into-the-
    // frontend convention every other template here already relies on
    // (reset-password, oauth2/callback, ...), even without a real frontend in this repo.
    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

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

    /** HMS v5 — {@code InviteService.createInvite}. Reuses the "generic" template
     *  rather than a dedicated one, same as {@link #onUserRoleMissing} below — neither
     *  is frequent enough on its own to warrant its own HTML file. */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserInvited(UserInvitedEvent event) {
        String heading = "You've been invited to Hospital Management System";
        String body = "<p>" + event.getInvitedByUsername() + " has invited you to join as <strong>"
                + event.getRoleName() + "</strong>.</p>"
                + "<p>Register with this exact email address within " + event.getExpiryDays()
                + " day(s) and that role will be assigned automatically.</p>";
        mailService.sendNotificationEmail(event.getEmail(), event.getEmail(),
                "You've been invited to HMS", heading, body, "Register now", event.getRegisterUrl());
    }

    /** HMS v5 — {@code AuthService.completeLogin}'s {@code notifyAdminsOfMissingRole},
     *  one event per currently-active admin. See {@link UserRoleMissingEvent}'s own
     *  Javadoc for why reaching this is a genuine edge case now, not the routine
     *  "just signed up" outcome it used to be. */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserRoleMissing(UserRoleMissingEvent event) {
        String heading = "An account has no assigned role";
        String body = "<p>" + event.getPendingUsername() + " (" + event.getPendingUserEmail()
                + ", id: " + event.getPendingUserId() + ") just attempted to log in but holds no active role "
                + "and can't be authenticated until one is assigned.</p>";
        String ctaUrl = frontendBaseUrl + "/admin/users/" + event.getPendingUserId();
        mailService.sendNotificationEmail(event.getAdminEmail(), event.getAdminUsername(),
                "HMS: an account needs a role", heading, body, "Review this account", ctaUrl);
    }
}
