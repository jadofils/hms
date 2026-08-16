package amalitech.hospital.management.repository.user.logs;

import amalitech.hospital.management.model.user.logs.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SystemLogRepository extends JpaRepository<SystemLog, String> {
    /** Used by {@code MaintenanceService.cleanupOldLogs} — a derived bulk delete, not a
     *  soft delete: {@link SystemLog} has no {@code deletedAt} column (it's an
     *  append-only log, not a user-facing record). */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
