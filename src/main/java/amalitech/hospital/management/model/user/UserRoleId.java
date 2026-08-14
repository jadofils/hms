package amalitech.hospital.management.model.user;

import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

/**
 * Composite key for UserRole (user_id + role_id).
 */
@Data
@Embeddable
public class UserRoleId implements Serializable {

    private String userId;
    private String roleId;
}
