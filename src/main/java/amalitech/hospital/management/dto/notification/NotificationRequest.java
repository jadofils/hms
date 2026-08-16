package amalitech.hospital.management.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NotificationRequest {

    @NotBlank(message = "Type is required")
    @Size(max = 100, message = "Type must be at most 100 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
            message = "Type must be lowercase, start with a letter, and can only contain letters, digits and hyphens")
    private String type;

    /** Optional — the user who triggered this notification, if any (a purely automated/
     *  system notification has no actor). */
    private String actorUserId;

    @NotEmpty(message = "At least one recipient is required")
    private List<@NotBlank(message = "Recipient id cannot be blank") String> recipients;

    /** Optional free-form JSON payload — must be valid JSON if provided. */
    private String payload;

    /** Optional free-form JSON (e.g. {@code ["email","sms"]}) — must be valid JSON if provided. */
    private String channels;

    /** Optional free-form JSON (e.g. per-channel delivery status) — must be valid JSON if provided. */
    private String status;

    /** Optional on create — the entity defaults to "normal". */
    @Pattern(regexp = "(?i)^(low|normal|high)$", message = "Priority must be one of: low, normal, high")
    private String priority;
}
