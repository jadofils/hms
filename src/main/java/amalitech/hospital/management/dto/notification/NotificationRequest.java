package amalitech.hospital.management.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NotificationRequest {

    @Schema(description = "Type of notification, lowercase, starting with a letter and containing only letters, digits and hyphens, up to 100 characters.",
            example = "appointment-reminder",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Type is required")
    @Size(max = 100, message = "Type must be at most 100 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
            message = "Type must be lowercase, start with a letter, and can only contain letters, digits and hyphens")
    private String type;

    /** Optional — the user who triggered this notification, if any (a purely automated/
     *  system notification has no actor). */
    @Schema(description = "Optional. Id of the user who triggered this notification; omitted for a purely automated/system notification.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String actorUserId;

    @Schema(description = "List of recipient user ids; at least one entry is required.",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "At least one recipient is required")
    private List<@NotBlank(message = "Recipient id cannot be blank") String> recipients;

    /** Optional free-form JSON payload — must be valid JSON if provided. */
    @Schema(description = "Optional. Must be valid JSON if provided. Free-form payload for the notification.",
            example = "{\"message\":\"Your appointment is confirmed\"}",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String payload;

    /** Optional free-form JSON (e.g. {@code ["email","sms"]}) — must be valid JSON if provided. */
    @Schema(description = "Optional. Must be valid JSON if provided. Delivery channels for the notification, e.g. [\"email\",\"sms\"].",
            example = "[\"email\",\"sms\"]",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String channels;

    /** Optional free-form JSON (e.g. per-channel delivery status) — must be valid JSON if provided. */
    @Schema(description = "Optional. Must be valid JSON if provided. Per-channel delivery status of the notification.",
            example = "{\"email\":\"sent\"}",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String status;

    /** Optional on create — the entity defaults to "normal". */
    @Schema(description = "Optional. Priority of the notification; must be one of: low, normal, high. Defaults to \"normal\" when omitted.",
            example = "normal",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(low|normal|high)$", message = "Priority must be one of: low, normal, high")
    private String priority;
}
