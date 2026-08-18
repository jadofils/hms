package amalitech.hospital.management.repository.user.logs;

import amalitech.hospital.management.model.user.logs.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SystemLogRepository extends JpaRepository<SystemLog, String> {
    /** Used by {@code MaintenanceService.cleanupOldLogs} — a derived bulk delete, not a
     *  soft delete: {@link SystemLog} has no {@code deletedAt} column (it's an
     *  append-only log, not a user-facing record). */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);

    /** Backs {@code SystemLogService.getSystemLogs}' {@code logLevel}-only filter — e.g.
     *  "show me every ERROR". */
    Page<SystemLog> findByLogLevel(String logLevel, Pageable pageable);

    /** Backs the {@code source}-only filter — a case-insensitive contains match rather
     *  than exact, since {@code source} is a {@code className.methodName} string (see
     *  {@code SystemLogWriter.record}) and a caller more realistically wants "every
     *  failure in RoleService" than the one exact fully-qualified string. */
    Page<SystemLog> findBySourceContainingIgnoreCase(String source, Pageable pageable);

    /** Backs the combined {@code logLevel} + {@code source} filter. */
    Page<SystemLog> findByLogLevelAndSourceContainingIgnoreCase(String logLevel, String source, Pageable pageable);
}
