package amalitech.hospital.management.repository.user;

import amalitech.hospital.management.model.user.UserInvite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserInviteRepository extends JpaRepository<UserInvite, String> {

    /** The one still-live invite for an email, if any — {@code InviteService.createInvite}
     *  rejects a second one while this still resolves, and
     *  {@code InviteService.consumeInviteIfAny} looks it up the moment a matching account
     *  is created. Ordered so a caller with (in principle) more than one historical row
     *  for the same email still only ever sees the most recent live one. */
    @EntityGraph(attributePaths = "role")
    Optional<UserInvite> findFirstByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(String email);

    /** Backs {@code GET /api/v1/invites} — every invite an admin can still act on
     *  (revoke), i.e. neither consumed nor already revoked. */
    @EntityGraph(attributePaths = {"role", "invitedBy"})
    Page<UserInvite> findByAcceptedAtIsNullAndRevokedAtIsNull(Pageable pageable);
}
