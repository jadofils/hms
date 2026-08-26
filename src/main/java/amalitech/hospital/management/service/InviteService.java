package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.invite.InviteRequest;
import amalitech.hospital.management.dto.invite.InviteResponse;
import amalitech.hospital.management.event.UserInvitedEvent;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.model.user.UserInvite;
import amalitech.hospital.management.model.user.role.Role;
import amalitech.hospital.management.repository.user.UserInviteRepository;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Admin-only invitations that pre-authorize a role for an email address before that
 * person has ever registered (HMS v5) — see {@code InviteController}. Consumed by
 * {@code UserService.createUser} (self-registration) and
 * {@code AuthService.createGoogleProvisionedUser} (first Google OAuth2 login): whichever
 * of the two actually creates the brand-new account calls {@link #consumeInviteIfAny}
 * right after, and the chosen role wins over the generic Guest fallback
 * ({@code UserService.assignDefaultGuestRole}) when a live invite exists.
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    private final UserInviteRepository userInviteRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.invite-ttl-days}")
    private final int inviteTtlDays;

    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

    @Transactional
    public InviteResponse createInvite(String invitedByUserId, InviteRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("A user with email '" + request.getEmail() + "' already exists");
        }
        userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(request.getEmail())
                .ifPresent(existing -> {
                    throw new ConflictException("An invite for '" + request.getEmail() + "' is already pending");
                });
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Role not found: " + request.getRoleId()));
        User invitedBy = userRepository.findById(invitedByUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + invitedByUserId));

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        UserInvite invite = new UserInvite();
        invite.setEmail(request.getEmail());
        invite.setRole(role);
        invite.setInvitedBy(invitedBy);
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plusDays(inviteTtlDays));
        UserInvite saved = userInviteRepository.save(invite);

        String registerUrl = frontendBaseUrl + "/register?email="
                + URLEncoder.encode(request.getEmail(), StandardCharsets.UTF_8);
        eventPublisher.publishEvent(new UserInvitedEvent(saved.getEmail(), role.getRoleName(),
                invitedBy.getUsername(), registerUrl, inviteTtlDays));
        return toResponse(saved);
    }

    public PagedModel<InviteResponse> getPendingInvites(Pageable pageable) {
        return new PagedModel<>(
                userInviteRepository.findByAcceptedAtIsNullAndRevokedAtIsNull(pageable).map(this::toResponse));
    }

    @Transactional
    public void revokeInvite(String inviteId) {
        UserInvite invite = userInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Invite not found: " + inviteId));
        if (invite.getAcceptedAt() != null) {
            throw new ConflictException("This invite has already been accepted");
        }
        if (invite.getRevokedAt() != null) {
            throw new ConflictException("This invite has already been revoked");
        }
        invite.setRevokedAt(LocalDateTime.now(ZoneId.systemDefault()));
        userInviteRepository.save(invite);
    }

    /**
     * Called the moment a brand-new account is created under {@code email} — by
     * {@code UserService.createUser} (self-registration) or
     * {@code AuthService.createGoogleProvisionedUser} (first Google OAuth2 login), never
     * on every login. Marks the invite consumed and returns the role id to assign
     * instead of the generic Guest fallback; an expired invite is left untouched (not
     * consumed, not deleted — an admin can still see it as pending until it's cleaned
     * up, though a caller here just falls through to Guest either way) and treated the
     * same as no invite at all.
     */
    @Transactional
    public Optional<String> consumeInviteIfAny(String email) {
        return userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(email)
                .filter(invite -> invite.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.systemDefault())))
                .map(invite -> {
                    invite.setAcceptedAt(LocalDateTime.now(ZoneId.systemDefault()));
                    userInviteRepository.save(invite);
                    return invite.getRole().getRoleId();
                });
    }

    private InviteResponse toResponse(UserInvite invite) {
        InviteResponse response = new InviteResponse();
        response.setInviteId(invite.getInviteId());
        response.setEmail(invite.getEmail());
        response.setRoleId(invite.getRole().getRoleId());
        response.setRoleName(invite.getRole().getRoleName());
        if (invite.getInvitedBy() != null) {
            response.setInvitedByUserId(invite.getInvitedBy().getUserId());
            response.setInvitedByUsername(invite.getInvitedBy().getUsername());
        }
        response.setCreatedAt(invite.getCreatedAt());
        response.setExpiresAt(invite.getExpiresAt());
        response.setAcceptedAt(invite.getAcceptedAt());
        response.setRevokedAt(invite.getRevokedAt());
        return response;
    }
}
