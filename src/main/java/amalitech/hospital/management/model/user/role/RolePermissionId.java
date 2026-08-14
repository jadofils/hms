package amalitech.hospital.management.model.user.role;

import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;


@Data
@Embeddable
public class RolePermissionId implements Serializable {

    private String roleId;
    private String permissionId;
}