package amalitech.hospital.management.exception.checked;

public class UserCheckedException extends Exception {
    public UserCheckedException(String message) {
        super(message);
    }

    public UserCheckedException(String message, Throwable cause) {
        super(message, cause);
    }
}