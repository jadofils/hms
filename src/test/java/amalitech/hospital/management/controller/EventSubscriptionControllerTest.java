package amalitech.hospital.management.controller;

import amalitech.hospital.management.aop.EventBus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code EventSubscriptionController} against the real, app-wide
 * {@link EventBus} singleton (already populated at startup by {@code NotificationEventListener}'s
 * real {@code @Subscribe} methods — see {@code EventBusTest}/{@code NotificationEventListenerTest}
 * for the mechanism's own isolated unit coverage). Every test restores the toggled
 * subscriber's enabled state afterward since this context is shared across the suite.
 */
class EventSubscriptionControllerTest extends AbstractControllerTest {

    private static final String SUBSCRIBER_NAME = "notification-on-appointment-created";

    @Autowired
    private EventBus eventBus;

    @AfterEach
    void restoreSubscriberState() {
        eventBus.setEnabled(SUBSCRIBER_NAME, true);
    }

    private boolean subscriberEnabled(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        for (JsonNode entry : data) {
            if (name.equals(entry.get("name").asText())) {
                return entry.get("enabled").asBoolean();
            }
        }
        throw new AssertionError("Subscriber not listed: " + name);
    }

    @Test
    void getSubscribers_listsTheRegisteredNotificationListeners() throws Exception {
        String token = adminToken();

        assertThat(subscriberEnabled(token, SUBSCRIBER_NAME)).isTrue();
    }

    @Test
    void unsubscribe_disablesTheListener() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/events/" + SUBSCRIBER_NAME + "/unsubscribe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(subscriberEnabled(token, SUBSCRIBER_NAME)).isFalse();
    }

    @Test
    void subscribe_reEnablesTheListener() throws Exception {
        String token = adminToken();
        eventBus.setEnabled(SUBSCRIBER_NAME, false);

        mockMvc.perform(post("/api/v1/events/" + SUBSCRIBER_NAME + "/subscribe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(subscriberEnabled(token, SUBSCRIBER_NAME)).isTrue();
    }

    @Test
    void subscribe_returnsNotFound_whenNameUnknown() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/events/bogus-name/subscribe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
