package amalitech.hospital.management.exception.runtime;

public class BadRequestException extends ApplicationException {
    public BadRequestException(String message) {
        super(message);
    }
}