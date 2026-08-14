package amalitech.hospital.management.model.user;

import amalitech.hospital.management.model.user.role.Role;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity model for the `user_roles` table.
 * Junction table for many-to-many relationship between users and roles.
 */
@Data
@Entity
@Table(name = "user_roles")
public class UserRole {

    /** Composite PK — (user_id, role_id) */
    @EmbeddedId
    private UserRoleId id;

    /** FK -> users */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_userrole_user"))
    private User user;

    /** FK -> roles */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_userrole_role"))
    private Role role;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
