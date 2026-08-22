package amalitech.hospital.management.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code AuthService.forgotPassword} once the Redis reset token is already
 * written — HMS v5, Epic 2. Unlike the other three {@code MailEventListener} events,
 * {@code forgotPassword} itself is not {@code @Transactional} — there's no active
 * transaction for {@code @TransactionalEventListener} to defer to, which is exactly why
 * {@code MailEventListener}'s handler for this (and, uniformly, the other three) sets
 * {@code fallbackExecution = true}: without it, a listener published outside any
 * transaction would silently never fire at all. See {@link UserRegisteredEvent}'s own
 * Javadoc for why this carries only primitives, never the {@code User} entity itself.
 */
public class PasswordResetRequestedEvent extends ApplicationEvent {

    private final String email;
    private final String recipientName;
    private final String resetToken;
    private final String resetUrl;
    private final int expiryMinutes;

    public PasswordResetRequestedEvent(String email, String recipientName, String resetToken,
                                        String resetUrl, int expiryMinutes) {
        super(email);
        this.email = email;
        this.recipientName = recipientName;
        this.resetToken = resetToken;
        this.resetUrl = resetUrl;
        this.expiryMinutes = expiryMinutes;
    }

    public String getEmail() {
        return email;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getResetToken() {
        return resetToken;
    }

    public String getResetUrl() {
        return resetUrl;
    }

    public int getExpiryMinutes() {
        return expiryMinutes;
    }
}
