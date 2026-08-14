package amalitech.hospital.management.model.user.role;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity model for the `role_permissions` table.
 * Junction table for many-to-many relationship between roles and permissions.
 */
@Data
@Entity
@Table(name = "role_permissions")
public class RolePermission {

    /** Composite PK — (role_id, permission_id) */
    @EmbeddedId
    private RolePermissionId id;

    /** FK -> roles */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_rolepermission_role"))
    private Role role;

    /** FK -> permissions */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_rolepermission_permission"))
    private Permission permission;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
