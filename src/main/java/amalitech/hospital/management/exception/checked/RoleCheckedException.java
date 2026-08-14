package amalitech.hospital.management.exception.checked;

public class RoleCheckedException extends Exception {
    public RoleCheckedException(String message) {
        super(message);
    }
    public RoleCheckedException(String message, Throwable cause) {
        super(message, cause);
    }
}