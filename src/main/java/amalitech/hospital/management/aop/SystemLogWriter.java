package amalitech.hospital.management.aop;

import amalitech.hospital.management.model.user.logs.SystemLog;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Persists one {@link SystemLog} row. Two callers today:
 * <ul>
 *   <li>{@link LoggingAspect}'s failure branch — every service-layer exception, generic
 *       by necessity (it never logs argument or return values — see that class's own
 *       Javadoc on why — so the message here is just the exception's own text).</li>
 *   <li>{@code AuthService.login}/{@code loginWithGoogle} (HMS v4, Epic 5.2) — a
 *       dedicated security-event log for authentication attempts specifically, which
 *       deliberately <em>does</em> include the attempted username/email (never the
 *       password) and source IP, since "which account was targeted, from where" is
 *       exactly what brute-force detection needs and {@code LoggingAspect}'s own
 *       generic, argument-blind failure log can't provide.</li>
 * </ul>
 * Deliberately its own small component in the {@code aop} package rather than a
 * {@code service}-package class: {@link LoggingAspect}'s pointcut wraps every method in
 * {@code service}, so a {@code service}-package writer would itself get logged (and
 * re-entrantly call back into this same failure path) every time {@link LoggingAspect}
 * used it. {@code AuthService} calling this class directly has no such issue — this
 * class isn't itself a service-layer method {@code LoggingAspect}'s pointcut matches.
 *
 * {@code REQUIRES_NEW} is the whole point of this being a separate component at all —
 * the failing call {@code LoggingAspect} is reporting on (or, for the auth case, the
 * transaction {@code AuthService.login} is about to fail out of) is very often itself
 * {@code @Transactional} and about to roll back; without a genuinely new transaction,
 * this row would be written to a transaction that's already marked for rollback and
 * silently vanish along with it, defeating the purpose of logging the failure at all.
 */
@Component
@RequiredArgsConstructor
public class SystemLogWriter {

    private static final Logger log = LoggerFactory.getLogger(SystemLogWriter.class);

    private final SystemLogRepository systemLogRepository;

    // Fires on a service-layer failure (LoggingAspect.persistFailure) or an explicit
    // authentication security event (AuthService.login/loginWithGoogle) — always in a
    // fresh REQUIRES_NEW transaction, independent of whatever the caller's own
    // transaction does next.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String logLevel, String source, String message) {
        log.debug("SystemLogWriter.record invoked — called by LoggingAspect.persistFailure");
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
