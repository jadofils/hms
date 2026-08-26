package amalitech.hospital.management.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code AuthService.completeLogin}'s {@code notifyAdminsOfMissingRole} —
 * HMS v5 — once per currently-active admin, the moment a login attempt resolves to zero
 * active roles at all. Since {@code UserService.assignDefaultGuestRole} now grants every
 * brand-new self-service account the Guest role automatically, reaching this is no
 * longer the routine "just signed up" case — it means every role an account held
 * (including Guest) was explicitly revoked afterward, which is genuinely worth an
 * admin's attention. Carries only primitives, never the {@code User} entity itself —
 * same reasoning as every other mail-triggering event (see
 * {@code UserRegisteredEvent}'s own Javadoc).
 */
public class UserRoleMissingEvent extends ApplicationEvent {

    private final String adminEmail;
    private final String adminUsername;
    private final String pendingUserEmail;
    private final String pendingUsername;
    private final String pendingUserId;

    public UserRoleMissingEvent(String adminEmail, String adminUsername, String pendingUserEmail,
            String pendingUsername, String pendingUserId) {
        super(pendingUserEmail);
        this.adminEmail = adminEmail;
        this.adminUsername = adminUsername;
        this.pendingUserEmail = pendingUserEmail;
        this.pendingUsername = pendingUsername;
        this.pendingUserId = pendingUserId;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getPendingUserEmail() {
        return pendingUserEmail;
    }

    public String getPendingUsername() {
        return pendingUsername;
    }

    public String getPendingUserId() {
        return pendingUserId;
    }
}
