package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.Subscribe;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An in-house pub-sub registry backing {@code @Subscribe} — deliberately not built on
 * Spring's own {@code ApplicationEventPublisher}/{@code ApplicationListener}, which has
 * no built-in mechanism to toggle a single listener off/on at runtime, the exact thing
 * {@code EventSubscriptionController}'s subscribe/unsubscribe endpoints need.
 *
 * At {@code @PostConstruct}, walks every bean in the context for {@code @Subscribe}-
 * annotated methods — the same "walk every bean's methods for the annotation" idea as
 * {@link ScheduledMaintenanceRegistrar}, just triggered once from inside this bean over
 * every bean up front, rather than a {@code BeanPostProcessor} intercepting each bean as
 * it's created. Resolves each bean's real class via {@link AopUtils#getTargetClass} on
 * the concrete (already-created) bean instance — never {@code applicationContext.getType}
 * predictions, which for an already-instantiated singleton can return the CGLIB proxy
 * class instead of the target class, and a proxy's overriding methods don't carry the
 * original method's annotations under plain reflection (see
 * {@link ScheduledMaintenanceRegistrar}'s own javadoc for the same risk).
 */
@Component
@RequiredArgsConstructor
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final ApplicationContext applicationContext;

    private final Map<String, Subscriber> subscribersByName = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Subscriber>> subscribersByEvent = new ConcurrentHashMap<>();

    @PostConstruct
    void scanForSubscribers() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (BeansException e) {
                // Some bean definitions (scoped proxies, infrastructure placeholders)
                // can't be resolved this way — skip rather than fail startup over a bean
                // that was never going to declare a @Subscribe method anyway.
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getMethods()) {
                Subscribe annotation = method.getAnnotation(Subscribe.class);
                if (annotation == null) {
                    continue;
                }
                register(annotation, bean, method);
            }
        }
    }

    private void register(Subscribe annotation, Object bean, Method method) {
        Subscriber subscriber = new Subscriber(annotation.name(), annotation.event(), bean, method, new AtomicBoolean(true));
        subscribersByName.put(annotation.name(), subscriber);
        subscribersByEvent.computeIfAbsent(annotation.event(), key -> new CopyOnWriteArrayList<>()).add(subscriber);
        log.info("Registered event subscriber '{}' for {}", annotation.name(), annotation.event().getSimpleName());
    }

    /**
     * Invokes every currently-enabled subscriber registered for {@code event}'s exact
     * class. A subscriber's own failure is logged and swallowed — one broken listener
     * must never undo or block the publishing call site's real work (the create that
     * just succeeded), nor stop any other subscriber for the same event, the same
     * "cross-cutting concern never breaks the real work" principle
     * {@code EmailAspect}/{@code LoggingAspect} already follow.
     */
    public void publish(Object event) {
        List<Subscriber> subscribers = subscribersByEvent.get(event.getClass());
        if (subscribers == null) {
            return;
        }
        for (Subscriber subscriber : subscribers) {
            if (!subscriber.enabled.get()) {
                continue;
            }
            try {
                subscriber.method.invoke(subscriber.bean, event);
            } catch (Exception e) {
                log.error("Event subscriber '{}' failed handling {}",
                        subscriber.name, event.getClass().getSimpleName(), e);
            }
        }
    }

    public List<SubscriberStatus> listSubscribers() {
        return subscribersByName.values().stream()
                .map(s -> new SubscriberStatus(s.name, s.eventType.getSimpleName(), s.enabled.get()))
                .toList();
    }

    /** Flips a subscriber's enabled flag — the actual "subscribe"/"unsubscribe" toggle;
     *  {@link #publish} just skips disabled entries, rather than deregistering anything. */
    public void setEnabled(String name, boolean enabled) {
        Subscriber subscriber = subscribersByName.get(name);
        if (subscriber == null) {
            throw new NotFoundException("No such event subscriber: " + name);
        }
        subscriber.enabled.set(enabled);
    }

    private record Subscriber(String name, Class<?> eventType, Object bean, Method method, AtomicBoolean enabled) {
    }

    public record SubscriberStatus(String name, String event, boolean enabled) {
    }
}
