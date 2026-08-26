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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock private UserInviteRepository userInviteRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InviteService inviteService;

    private Role doctorRole;
    private User admin;
    private UserInvite existingInvite;

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(userInviteRepository, roleRepository, userRepository,
                eventPublisher, 7, "http://localhost:3000");

        doctorRole = new Role();
        doctorRole.setRoleId("role-1");
        doctorRole.setRoleName("Doctor");

        admin = new User();
        admin.setUserId("admin-1");
        admin.setUsername("admin");

        existingInvite = new UserInvite();
        existingInvite.setInviteId("invite-1");
        existingInvite.setEmail("newperson@example.com");
        existingInvite.setRole(doctorRole);
        existingInvite.setInvitedBy(admin);
        existingInvite.setCreatedAt(LocalDateTime.now());
        existingInvite.setExpiresAt(LocalDateTime.now().plusDays(7));
    }

    // ── createInvite ─────────────────────────────────────────────────────────

    @Test
    void createInvite_throwsConflict_whenAUserAlreadyExistsWithThatEmail() {
        when(userRepository.existsByEmail("newperson@example.com")).thenReturn(true);
        InviteRequest request = requestFor("newperson@example.com", "role-1");

        assertThatThrownBy(() -> inviteService.createInvite("admin-1", request))
                .isInstanceOf(ConflictException.class);
        verify(userInviteRepository, never()).save(any());
    }

    @Test
    void createInvite_throwsConflict_whenAnInviteIsAlreadyPendingForThatEmail() {
        when(userRepository.existsByEmail("newperson@example.com")).thenReturn(false);
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("newperson@example.com"))
                .thenReturn(Optional.of(existingInvite));
        InviteRequest request = requestFor("newperson@example.com", "role-1");

        assertThatThrownBy(() -> inviteService.createInvite("admin-1", request))
                .isInstanceOf(ConflictException.class);
        verify(userInviteRepository, never()).save(any());
    }

    @Test
    void createInvite_throwsNotFound_whenRoleAbsent() {
        when(userRepository.existsByEmail("newperson@example.com")).thenReturn(false);
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("newperson@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findById("missing-role")).thenReturn(Optional.empty());
        InviteRequest request = requestFor("newperson@example.com", "missing-role");

        assertThatThrownBy(() -> inviteService.createInvite("admin-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInvite_throwsNotFound_whenInvitingUserAbsent() {
        when(userRepository.existsByEmail("newperson@example.com")).thenReturn(false);
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("newperson@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(doctorRole));
        when(userRepository.findById("missing-admin")).thenReturn(Optional.empty());
        InviteRequest request = requestFor("newperson@example.com", "role-1");

        assertThatThrownBy(() -> inviteService.createInvite("missing-admin", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createInvite_savesAndPublishesAnEvent_whenValid() {
        when(userRepository.existsByEmail("newperson@example.com")).thenReturn(false);
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("newperson@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(doctorRole));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(userInviteRepository.save(any(UserInvite.class))).thenAnswer(inv -> inv.getArgument(0));
        InviteRequest request = requestFor("newperson@example.com", "role-1");

        InviteResponse response = inviteService.createInvite("admin-1", request);

        assertThat(response.getEmail()).isEqualTo("newperson@example.com");
        assertThat(response.getRoleName()).isEqualTo("Doctor");
        assertThat(response.getInvitedByUsername()).isEqualTo("admin");
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));

        ArgumentCaptor<UserInvitedEvent> eventCaptor = ArgumentCaptor.forClass(UserInvitedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo("newperson@example.com");
        assertThat(eventCaptor.getValue().getRoleName()).isEqualTo("Doctor");
        assertThat(eventCaptor.getValue().getInvitedByUsername()).isEqualTo("admin");
        assertThat(eventCaptor.getValue().getRegisterUrl()).startsWith("http://localhost:3000/register?email=");
    }

    // ── getPendingInvites ────────────────────────────────────────────────────

    @Test
    void getPendingInvites_mapsPageOfEntitiesToResponses() {
        when(userInviteRepository.findByAcceptedAtIsNullAndRevokedAtIsNull(any()))
                .thenReturn(new PageImpl<>(List.of(existingInvite)));

        PagedModel<InviteResponse> result = inviteService.getPendingInvites(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("newperson@example.com");
    }

    // ── revokeInvite ─────────────────────────────────────────────────────────

    @Test
    void revokeInvite_throwsNotFound_whenAbsent() {
        when(userInviteRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.revokeInvite("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokeInvite_throwsConflict_whenAlreadyAccepted() {
        existingInvite.setAcceptedAt(LocalDateTime.now());
        when(userInviteRepository.findById("invite-1")).thenReturn(Optional.of(existingInvite));

        assertThatThrownBy(() -> inviteService.revokeInvite("invite-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void revokeInvite_throwsConflict_whenAlreadyRevoked() {
        existingInvite.setRevokedAt(LocalDateTime.now());
        when(userInviteRepository.findById("invite-1")).thenReturn(Optional.of(existingInvite));

        assertThatThrownBy(() -> inviteService.revokeInvite("invite-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void revokeInvite_setsRevokedAt_whenStillPending() {
        when(userInviteRepository.findById("invite-1")).thenReturn(Optional.of(existingInvite));
        when(userInviteRepository.save(any(UserInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        inviteService.revokeInvite("invite-1");

        assertThat(existingInvite.getRevokedAt()).isNotNull();
    }

    // ── consumeInviteIfAny ───────────────────────────────────────────────────

    @Test
    void consumeInviteIfAny_returnsEmpty_whenNoInvitePending() {
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("nobody@example.com"))
                .thenReturn(Optional.empty());

        assertThat(inviteService.consumeInviteIfAny("nobody@example.com")).isEmpty();
    }

    @Test
    void consumeInviteIfAny_returnsEmpty_whenTheOnlyInviteHasExpired() {
        existingInvite.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("newperson@example.com"))
                .thenReturn(Optional.of(existingInvite));

        assertThat(inviteService.consumeInviteIfAny("newperson@example.com")).isEmpty();
        verify(userInviteRepository, never()).save(any());
    }

    @Test
    void consumeInviteIfAny_marksAcceptedAndReturnsTheRoleId_whenLiveInviteExists() {
        when(userInviteRepository
                .findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc("newperson@example.com"))
                .thenReturn(Optional.of(existingInvite));
        when(userInviteRepository.save(any(UserInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<String> roleId = inviteService.consumeInviteIfAny("newperson@example.com");

        assertThat(roleId).contains("role-1");
        assertThat(existingInvite.getAcceptedAt()).isNotNull();
    }

    private static InviteRequest requestFor(String email, String roleId) {
        InviteRequest request = new InviteRequest();
        request.setEmail(email);
        request.setRoleId(roleId);
        return request;
    }
}
