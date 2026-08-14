package amalitech.hospital.management.exception.runtime;

/**
 * Base application exception.
 * All custom exceptions (runtime + checked) extend this.
 */
public abstract class ApplicationException extends RuntimeException {
    public ApplicationException(String message) {
        super(message);
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
