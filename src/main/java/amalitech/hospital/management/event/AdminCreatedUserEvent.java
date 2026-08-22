package amalitech.hospital.management.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code UserService.createUserByAdmin} once the new account is saved — HMS
 * v5, Epic 2. Carries the one-time generated password as a plain field for exactly as
 * long as it takes {@code MailEventListener} to email it — never logged, never persisted,
 * never returned in any API response (same guarantee the direct
 * {@code mailService.sendGeneratedPasswordEmail} call it replaces already had). See
 * {@link UserRegisteredEvent}'s own Javadoc for why this carries only primitives, never
 * the {@code User} entity itself.
 */
public class AdminCreatedUserEvent extends ApplicationEvent {

    private final String email;
    private final String recipientName;
    private final String generatedPassword;

    public AdminCreatedUserEvent(String email, String recipientName, String generatedPassword) {
        super(email);
        this.email = email;
        this.recipientName = recipientName;
        this.generatedPassword = generatedPassword;
    }

    public String getEmail() {
        return email;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getGeneratedPassword() {
        return generatedPassword;
    }
}
