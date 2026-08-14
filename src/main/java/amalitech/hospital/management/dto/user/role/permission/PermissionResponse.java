package amalitech.hospital.management.dto.user.role.permission;

import lombok.Data;



@Data
public class PermissionResponse {
    private String permissionId;
    private String resource;
    private String action;
}
