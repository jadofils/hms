package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.ScheduledMaintenance;
import amalitech.hospital.management.enums.MaintenanceInterval;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic background maintenance — see {@code ScheduledMaintenanceRegistrar} for how
 * the {@code @ScheduledMaintenance}-annotated methods below actually get invoked.
 *
 * The annotation's {@code interval}/{@code unit} only control how often each method
 * *runs*; the retention/idle thresholds each one checks *inside* the method are
 * separate, independently configurable values (how often to check and what counts as
 * stale are different concerns that just happen to often share a similar magnitude).
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final SystemLogRepository systemLogRepository;
    private final UserRepository userRepository;

    @Value("${app.maintenance.log-retention-days}")
    private final long logRetentionDays;

    @Value("${app.maintenance.idle-user-days}")
    private final long idleUserDays;

    /** Purges {@code SystemLog} rows older than the retention window — see
     *  {@code LoggingAspect.logTiming}'s failure branch for where they're written. */
    @Transactional
    @ScheduledMaintenance(value = "log-cleanup", interval = 1, unit = MaintenanceInterval.DAYS)
    public void cleanupOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(logRetentionDays);
        systemLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Log cleanup complete: removed system logs older than {}", cutoff);
    }

    /** Deactivates active users who haven't logged in for longer than the idle
     *  window — see {@code UserRepository.findActiveUsersIdleSince}. */
    @Transactional
    @ScheduledMaintenance(value = "deactivate-idle-users", interval = 6, unit = MaintenanceInterval.HOURS)
    public void deactivateIdleUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(idleUserDays);
        List<User> idleUsers = userRepository.findActiveUsersIdleSince(cutoff);
        LocalDateTime now = LocalDateTime.now();
        for (User user : idleUsers) {
            user.setIsActive(false);
            user.setUpdatedAt(now);
            userRepository.save(user);
        }
        log.info("Idle-user deactivation complete: {} account(s) deactivated (idle since before {})",
                idleUsers.size(), cutoff);
    }
}
