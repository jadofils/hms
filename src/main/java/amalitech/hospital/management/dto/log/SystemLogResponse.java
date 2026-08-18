package amalitech.hospital.management.dto.log;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemLogResponse {
    private String logId;
    private String logLevel;
    private String source;
    private String message;
    /** {@code null} for the vast majority of rows today — {@code SystemLogWriter.record}
     *  (the only current writer, see {@code LoggingAspect}'s failure branch) never sets
     *  {@code SystemLog.user}, since a failing service call isn't always running inside
     *  an authenticated request. Exposed as a raw id (no nested {@code UserResponse}) —
     *  logs are read-only, high-volume, append-only records, not a place to eager-load a
     *  whole user profile per row. */
    private String userId;
    private LocalDateTime createdAt;
}
