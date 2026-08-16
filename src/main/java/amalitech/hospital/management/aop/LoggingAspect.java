package amalitech.hospital.management.aop;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Centralized logging/monitoring across the entire service layer — one pointcut
 * ({@link #serviceLayer()}), three advice types, each responsible for a distinct concern
 * rather than three aspects logging the same event redundantly:
 * <ul>
 *   <li>{@link #logEntry} ({@code @Before}) — logs that a call started.</li>
 *   <li>{@link #logExit} ({@code @After}) — logs that a call finished, success or
 *       failure alike (this is the "finally" advice type — it has no access to the
 *       return value or a thrown exception, unlike {@code @AfterReturning}/
 *       {@code @AfterThrowing}).</li>
 *   <li>{@link #logTiming} ({@code @Around}) — the only advice type that can measure
 *       elapsed time and see both the outcome and the exception, so it owns both.</li>
 * </ul>
 *
 * <b>Deliberately never logs raw argument or return values.</b> Service method
 * parameters include DTOs like {@code UserRequest}/{@code ChangePasswordRequest} that
 * carry plaintext passwords, and {@code PatientRequest}/{@code AppointmentRequest} that
 * carry patient PII — every one of them is a Lombok {@code @Data} class, so its
 * generated {@code toString()} would otherwise dump those values straight into the
 * application log the moment anything here logged an argument or a return value. Only
 * the class/method name, argument count, elapsed time, and exception message are ever
 * logged.
 *
 * {@code @Order(1)}: none of the other three service-layer aspects
 * ({@code AlgorithmAspect}/{@code FindUserDataAspect}/{@code SqlQueryBuilderAspect})
 * declare an order, so they default to {@code Ordered.LOWEST_PRECEDENCE} and, without
 * one here, this aspect's relative nesting against them would be unspecified — and each
 * of those replaces its annotated method's execution instead of calling {@code proceed()}
 * all the way to the real body. If this aspect ended up nested *inside* one of them, its
 * advice would simply never run for that call. A low (but not
 * {@code Ordered.HIGHEST_PRECEDENCE}) order forces this aspect to always wrap outermost
 * — literal {@code HIGHEST_PRECEDENCE} would instead push it ahead of Spring's own
 * internal {@code ExposeInvocationInterceptor}, which {@code @Before}/{@code @After}
 * advice on a named pointcut relies on, breaking join point matching entirely.
 */
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    private final SystemLogWriter systemLogWriter;

    @Pointcut("execution(* amalitech.hospital.management.service..*(..))")
    public void serviceLayer() {
    }

    @Before("serviceLayer()")
    public void logEntry(JoinPoint joinPoint) {
        log.info("→ {}.{}() [{} arg(s)]",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                joinPoint.getArgs().length);
    }

    @After("serviceLayer()")
    public void logExit(JoinPoint joinPoint) {
        log.debug("← {}.{}() completed",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName());
    }

    /**
     * Logs, then rethrows the exact original {@code Throwable} unchanged — deliberate,
     * not the "log and swallow" vs. "wrap and rethrow" choice this method's flagged
     * finding otherwise assumes. This advice's whole point is recording timing/outcome
     * for every service call including failures; rethrowing the original instance
     * (rather than wrapping it in a new exception "with context") is what lets
     * {@code GlobalExceptionHandler}'s per-type {@code @ExceptionHandler}s (and anything
     * else upstream matching on exception type) keep working exactly as if this aspect
     * didn't exist.
     */
    @SuppressWarnings("java:S2139")
    @Around("serviceLayer()")
    public Object logTiming(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("✓ {}.{}() finished in {}ms", className, methodName, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.error("✗ {}.{}() failed after {}ms: {}",
                    className, methodName, System.currentTimeMillis() - start, ex.getMessage());
            persistFailure(className, methodName, ex);
            throw ex;
        }
    }

    /** Gives {@code SystemLog} — otherwise entities-only scaffolding with no write-side
     *  caller anywhere — a real, always-on source of rows: every service-layer failure,
     *  not just the ones an admin happens to be watching the console for. Failing to
     *  persist the log entry itself must never mask the real exception the caller is
     *  about to see — this is purely a side-channel record, not part of the actual
     *  call's outcome. */
    private void persistFailure(String className, String methodName, Throwable ex) {
        try {
            systemLogWriter.record("ERROR", className + "." + methodName, ex.getMessage());
        } catch (Exception loggingFailure) {
            log.warn("Failed to persist SystemLog entry for {}.{}(): {}",
                    className, methodName, loggingFailure.getMessage());
        }
    }
}
