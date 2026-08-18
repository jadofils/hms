package amalitech.hospital.management.aop;

import amalitech.hospital.management.model.user.logs.SystemLog;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Persists one {@link SystemLog} row — used only by {@link LoggingAspect}'s failure
 * branch. Deliberately its own small component in the {@code aop} package rather than a
 * {@code service}-package class: {@link LoggingAspect}'s pointcut wraps every method in
 * {@code service}, so a {@code service}-package writer would itself get logged (and
 * re-entrantly call back into this same failure path) every time it ran.
 *
 * {@code REQUIRES_NEW} is the whole point of this being a separate component at all —
 * the failing call {@code LoggingAspect} is reporting on is very often itself
 * {@code @Transactional} and about to roll back; without a genuinely new transaction,
 * this row would be written to a transaction that's already marked for rollback and
 * silently vanish along with it, defeating the purpose of logging the failure at all.
 */
@Component
@RequiredArgsConstructor
public class SystemLogWriter {

    private final SystemLogRepository systemLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String logLevel, String source, String message) {
        SystemLog entry = new SystemLog();
        entry.setLogLevel(logLevel);
        entry.setSource(source);
        // The message column is NOT NULL, but plenty of exceptions (NPEs especially)
        // have a null getMessage() — fall back to the exception's simple class name
        // rather than letting that violate the column constraint.
        entry.setMessage(message == null || message.isBlank() ? "(no message)" : message);
        entry.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        systemLogRepository.save(entry);
    }
}
