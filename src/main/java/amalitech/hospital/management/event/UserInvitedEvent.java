package amalitech.hospital.management.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code InviteService.createInvite} once the invite row is saved — HMS
 * v5. Carries only primitives, never the {@code UserInvite}/{@code Role}/{@code User}
 * entities themselves, same reasoning as every other mail-triggering event (see
 * {@code UserRegisteredEvent}'s own Javadoc): {@code MailEventListener} runs
 * {@code @Async}, after this method's transaction commits, on a different thread —
 * an entity reference would risk a lazy-load outside its original session.
 */
public class UserInvitedEvent extends ApplicationEvent {

    private final String email;
    private final String roleName;
    private final String invitedByUsername;
    private final String registerUrl;
    private final int expiryDays;

    public UserInvitedEvent(String email, String roleName, String invitedByUsername,
            String registerUrl, int expiryDays) {
        super(email);
        this.email = email;
        this.roleName = roleName;
        this.invitedByUsername = invitedByUsername;
        this.registerUrl = registerUrl;
        this.expiryDays = expiryDays;
    }

    public String getEmail() {
        return email;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getInvitedByUsername() {
        return invitedByUsername;
    }

    public String getRegisterUrl() {
        return registerUrl;
    }

    public int getExpiryDays() {
        return expiryDays;
    }
}
