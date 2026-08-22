package amalitech.hospital.management.event;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEvent;

/**
 * Published by both {@code AuthService.resetPassword} and {@code AuthService.changePassword}
 * once the new password hash is saved — HMS v5, Epic 2. One event type, two publish
 * sites: both mean the exact same thing to a listener ("this account's password just
 * changed, tell whoever holds the mailbox"), so there's no reason for two event classes.
 * See {@link UserRegisteredEvent}'s own Javadoc for why this carries only primitives,
 * never the {@code User} entity itself.
 */
public class PasswordChangedEvent extends ApplicationEvent {

    private final String email;
    private final String recipientName;
    private final LocalDateTime changedAt;

    public PasswordChangedEvent(String email, String recipientName, LocalDateTime changedAt) {
        super(email);
        this.email = email;
        this.recipientName = recipientName;
        this.changedAt = changedAt;
    }

    public String getEmail() {
        return email;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
