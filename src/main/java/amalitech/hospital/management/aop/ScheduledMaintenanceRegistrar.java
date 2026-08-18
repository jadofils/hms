package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.ScheduledMaintenance;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Finds every {@code @ScheduledMaintenance}-annotated method across all beans at
 * startup and registers it with a real {@link TaskScheduler}. A {@code BeanPostProcessor}
 * is the correct extension point here — as opposed to another {@code @Around}/
 * {@code @Before} {@code @Aspect}, which only ever fires when *something else calls*
 * the annotated method. This is genuinely a different job: *creating new periodic
 * invocations* nothing else triggers, which is exactly how Spring's own
 * {@code ScheduledAnnotationBeanPostProcessor} (backing {@code @Scheduled}) works
 * internally.
 *
 * Resolves annotations against {@link AopUtils#getTargetClass}, not {@code bean.getClass()}
 * directly — {@code MaintenanceService} (like every other {@code @Service} in this
 * package) is wrapped in a CGLIB proxy by {@code LoggingAspect}'s blanket
 * service-layer pointcut, and a generated proxy subclass's overriding methods don't
 * automatically carry the original method's annotations under plain reflection.
 * {@code Method.invoke} is still called on the actual (possibly proxied) bean, so any
 * other cross-cutting advice on the same method (logging, transactions) still applies.
 */
@Component
@RequiredArgsConstructor
public class ScheduledMaintenanceRegistrar implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMaintenanceRegistrar.class);

    private final TaskScheduler taskScheduler;

    // Fires once per bean created by Spring at startup (every bean, not just
    // MaintenanceService) — deliberately no log line here: with dozens of beans in this
    // context, a per-bean debug log would drown out every other log line at startup for
    // no benefit, since almost none of them ever have a @ScheduledMaintenance method.
    // The log.info below, which only fires for an actual match, is the useful signal.
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : targetClass.getMethods()) {
            ScheduledMaintenance annotation = method.getAnnotation(ScheduledMaintenance.class);
            if (annotation == null) {
                continue;
            }
            Duration period = toDuration(annotation);
            log.info("Registering scheduled maintenance task '{}' ({}.{}()) every {}",
                    annotation.value(), targetClass.getSimpleName(), method.getName(), period);
            taskScheduler.scheduleAtFixedRate(() -> invoke(bean, method, annotation.value()), period);
        }
        return bean;
    }

    // Fires on each scheduled run of a task registered above — e.g.
    // MaintenanceService.cleanupOldLogs/deactivateIdleUsers on their configured interval.
    private void invoke(Object bean, Method method, String taskName) {
        log.debug("ScheduledMaintenanceRegistrar.invoke invoked — called by the TaskScheduler on each scheduled run (e.g. MaintenanceService.cleanupOldLogs)");
        try {
            method.invoke(bean);
        } catch (Exception e) {
            // A single failed run must never kill the scheduler thread or cancel future
            // runs — log and move on, same "cross-cutting concern never breaks the real
            // work" principle EmailAspect/LoggingAspect already follow.
            log.error("Scheduled maintenance task '{}' failed", taskName, e);
        }
    }

    // Fires once per @ScheduledMaintenance method found, called only from
    // postProcessAfterInitialization above to compute its scheduling period.
    private static Duration toDuration(ScheduledMaintenance annotation) {
        log.debug("ScheduledMaintenanceRegistrar.toDuration invoked — called by ScheduledMaintenanceRegistrar.postProcessAfterInitialization");
        return switch (annotation.unit()) {
            case HOURS -> Duration.ofHours(annotation.interval());
            case DAYS -> Duration.ofDays(annotation.interval());
        };
    }
}
