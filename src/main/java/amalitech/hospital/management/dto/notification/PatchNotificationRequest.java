package amalitech.hospital.management.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Partial-update counterpart to {@link NotificationRequest} — every field optional;
 * only the ones actually present in the request body get changed. See
 * {@code NotificationService.patchNotification}.
 *
 * {@code recipients} uses {@code @Size(min = 1)} rather than {@code NotificationRequest}'s
 * own {@code @NotEmpty} — {@code @NotEmpty} fails validation on a {@code null} list
 * (which is exactly what "omitted" looks like here), while {@code @Size} only checks
 * length when the list is actually present, so "at least one entry if you give any at
 * all" survives while "omit it entirely" stays valid.
 */
@Data
public class PatchNotificationRequest {

    @Schema(description = "Optional. Type of notification, lowercase, starting with a letter and containing only letters, digits and hyphens, up to 100 characters.",
            example = "appointment-reminder", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Type must be at most 100 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
            message = "Type must be lowercase, start with a letter, and can only contain letters, digits and hyphens")
    private String type;

    @Schema(description = "Optional. Id of the user who triggered this notification.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String actorUserId;

    @Schema(description = "Optional. List of recipient user ids; at least one entry if given.",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(min = 1, message = "At least one recipient is required")
    private List<@NotBlank(message = "Recipient id cannot be blank") String> recipients;

    @Schema(description = "Optional. Must be valid JSON if provided. Free-form payload for the notification.",
            example = "{\"message\":\"Your appointment is confirmed\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String payload;

    @Schema(description = "Optional. Must be valid JSON if provided. Delivery channels for the notification, e.g. [\"email\",\"sms\"].",
            example = "[\"email\",\"sms\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String channels;

    @Schema(description = "Optional. Must be valid JSON if provided. Per-channel delivery status of the notification.",
            example = "{\"email\":\"sent\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String status;

    @Schema(description = "Optional. Priority of the notification; must be one of: low, normal, high.",
            example = "normal", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(low|normal|high)$", message = "Priority must be one of: low, normal, high")
    private String priority;
}
