package amalitech.hospital.management.exception.checked;

public class PermissionCheckedException extends Exception {
    public PermissionCheckedException(String message) {
        super(message);
    }

    public PermissionCheckedException(String message, Throwable cause) {
        super(message, cause);
    }
}