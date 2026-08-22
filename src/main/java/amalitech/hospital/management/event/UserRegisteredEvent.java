package amalitech.hospital.management.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code UserService.sendVerificationEmail} (called from {@code createUser})
 * once the new account is saved and its Redis verification token already written — HMS
 * v5, Epic 2. Deliberately carries only primitives, never the {@code User} entity itself:
 * {@code MailEventListener}'s handler for this runs later ({@code @TransactionalEventListener},
 * possibly on a different thread via {@code @Async}), after the publishing transaction's
 * session is gone — touching a lazy field on a stale entity reference at that point would
 * throw {@code LazyInitializationException} (or worse, silently reopen a session if
 * {@code open-in-view} were still on, defeating the point of this pass having just turned
 * it off). Unlike {@link AppointmentCreatedEvent}/{@link PrescriptionCreatedEvent}/etc.
 * (the pre-existing {@code EventBus}/{@code @Subscribe} events, whose listeners run
 * synchronously in the same transaction/session, so wrapping the raw entity is safe there)
 * — see {@code MailEventListener}'s own Javadoc for why this is a separate mechanism, not
 * a replacement for that one.
 */
public class UserRegisteredEvent extends ApplicationEvent {

    private final String email;
    private final String recipientName;
    private final String verifyUrl;
    private final int expiryHours;

    public UserRegisteredEvent(String email, String recipientName, String verifyUrl, int expiryHours) {
        super(email);
        this.email = email;
        this.recipientName = recipientName;
        this.verifyUrl = verifyUrl;
        this.expiryHours = expiryHours;
    }

    public String getEmail() {
        return email;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getVerifyUrl() {
        return verifyUrl;
    }

    public int getExpiryHours() {
        return expiryHours;
    }
}
