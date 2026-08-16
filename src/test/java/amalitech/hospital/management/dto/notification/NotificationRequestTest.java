package amalitech.hospital.management.dto.notification;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRequestTest extends ValidationTestBase {

    private static NotificationRequest valid() {
        NotificationRequest request = new NotificationRequest();
        request.setType("appointment-created");
        request.setActorUserId("user-1");
        request.setRecipients(new ArrayList<>(List.of("user-2")));
        request.setPriority("normal");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void actorUserIdPayloadChannelsStatusAndPriority_areOptional() {
        NotificationRequest request = valid();
        request.setActorUserId(null);
        request.setPayload(null);
        request.setChannels(null);
        request.setStatus(null);
        request.setPriority(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void blankType_isRejected() {
        NotificationRequest request = valid();
        request.setType("");
        assertThat(hasViolationOn(request, "type")).isTrue();
    }

    @Test
    void uppercaseOrInvalidType_isRejected() {
        for (String bad : new String[]{"Appointment-Created", "APPOINTMENT_CREATED", "1-created", "created type"}) {
            NotificationRequest request = valid();
            request.setType(bad);
            assertThat(hasViolationOn(request, "type")).as("type '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void typeOver100Characters_isRejected() {
        NotificationRequest request = valid();
        request.setType("a".repeat(101));
        assertThat(hasViolationOn(request, "type")).isTrue();
    }

    @Test
    void emptyRecipients_isRejected() {
        NotificationRequest request = valid();
        request.setRecipients(List.of());
        assertThat(hasViolationOn(request, "recipients")).isTrue();
    }

    @Test
    void nullRecipients_isRejected() {
        NotificationRequest request = valid();
        request.setRecipients(null);
        assertThat(hasViolationOn(request, "recipients")).isTrue();
    }

    @Test
    void blankRecipientElement_isRejected() {
        NotificationRequest request = valid();
        request.setRecipients(new ArrayList<>(List.of("user-2", "   ")));
        // Hibernate Validator's path for a constraint on a List<String> element is
        // "recipients[1].<list element>", not the bare "recipients[1]".
        assertThat(hasViolationOn(request, "recipients[1].<list element>")).isTrue();
    }

    @Test
    void invalidPriority_isRejected() {
        NotificationRequest request = valid();
        request.setPriority("urgent");
        assertThat(hasViolationOn(request, "priority")).isTrue();
    }

    @Test
    void priority_everyAllowedValue_isCaseInsensitivelyAccepted() {
        for (String ok : new String[]{"low", "NORMAL", "High"}) {
            NotificationRequest request = valid();
            request.setPriority(ok);
            assertThat(hasViolationOn(request, "priority")).as("priority '%s' should be accepted", ok).isFalse();
        }
    }
}
