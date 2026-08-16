package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.Subscribe;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link EventBus#scanForSubscribers} is a plain {@code @PostConstruct} method a
 * manually-constructed {@code EventBus} can call directly (same reasoning as
 * {@code ScheduledMaintenanceRegistrarTest}: no real Spring proxy is needed to exercise
 * this reflection-driven bean walk) — a mocked {@link ApplicationContext} stands in for
 * the real bean registry.
 */
@ExtendWith(MockitoExtension.class)
class EventBusTest {

    @Mock private ApplicationContext applicationContext;

    private EventBus eventBus;

    record SampleEvent(String payload) {}
    record OtherEvent() {}

    static class SampleSubscriberBean {
        final AtomicInteger invocationCount = new AtomicInteger();

        @Subscribe(name = "sample-subscriber", event = SampleEvent.class)
        public void onSampleEvent(SampleEvent event) {
            invocationCount.incrementAndGet();
        }
    }

    static class PlainBean {
        public void doSomething() {
        }
    }

    private SampleSubscriberBean subscriberBean;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus(applicationContext);
        subscriberBean = new SampleSubscriberBean();
    }

    private void scanWith(Object... beans) {
        String[] names = new String[beans.length];
        for (int i = 0; i < beans.length; i++) {
            names[i] = "bean" + i;
            when(applicationContext.getBean(names[i])).thenReturn(beans[i]);
        }
        when(applicationContext.getBeanDefinitionNames()).thenReturn(names);
        eventBus.scanForSubscribers();
    }

    @Test
    void publish_invokesEnabledSubscriber_forMatchingEvent() {
        scanWith(subscriberBean, new PlainBean());

        eventBus.publish(new SampleEvent("hello"));

        assertThat(subscriberBean.invocationCount.get()).isEqualTo(1);
    }

    @Test
    void publish_doesNothing_whenNoSubscriberRegisteredForEventType() {
        scanWith(subscriberBean);

        eventBus.publish(new OtherEvent());

        assertThat(subscriberBean.invocationCount.get()).isZero();
    }

    @Test
    void publish_skipsDisabledSubscriber() {
        scanWith(subscriberBean);
        eventBus.setEnabled("sample-subscriber", false);

        eventBus.publish(new SampleEvent("hello"));

        assertThat(subscriberBean.invocationCount.get()).isZero();
    }

    @Test
    void setEnabled_reEnablesASubscriber() {
        scanWith(subscriberBean);
        eventBus.setEnabled("sample-subscriber", false);
        eventBus.setEnabled("sample-subscriber", true);

        eventBus.publish(new SampleEvent("hello"));

        assertThat(subscriberBean.invocationCount.get()).isEqualTo(1);
    }

    @Test
    void setEnabled_throwsNotFound_whenNameUnknown() {
        scanWith(subscriberBean);

        assertThatThrownBy(() -> eventBus.setEnabled("bogus", false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listSubscribers_reflectsRegisteredNameEventAndEnabledState() {
        scanWith(subscriberBean);

        assertThat(eventBus.listSubscribers())
                .anySatisfy(status -> {
                    assertThat(status.name()).isEqualTo("sample-subscriber");
                    assertThat(status.event()).isEqualTo("SampleEvent");
                    assertThat(status.enabled()).isTrue();
                });
    }

    @Test
    void publish_swallowsSubscriberFailure_ratherThanPropagating() {
        class ThrowingBean {
            @Subscribe(name = "throwing-subscriber", event = SampleEvent.class)
            public void onSampleEvent(SampleEvent event) {
                throw new IllegalStateException("boom");
            }
        }
        scanWith(new ThrowingBean());

        // Must not propagate — a broken listener must never break the publishing call
        // site's real work or any other subscriber for the same event.
        eventBus.publish(new SampleEvent("hello"));
    }
}
