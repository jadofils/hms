package amalitech.hospital.management.dto.patient;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Especially provokes {@code dob} — a patient's birth date must always be in the past. */
class PatientRequestTest extends ValidationTestBase {

    private static PatientRequest valid() {
        PatientRequest request = new PatientRequest();
        request.setFirstName("Alice");
        request.setLastName("Doe");
        request.setDob(LocalDate.of(1990, 1, 1));
        request.setGender("M");
        request.setPhone("1234567");
        request.setEmail("alice@example.com");
        request.setAddress("123 Main St");
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    // ── dob ──────────────────────────────────────────────────────────────────

    @Test
    void dob_null_isRejected() {
        PatientRequest request = valid();
        request.setDob(null);
        assertThat(hasViolationOn(request, "dob")).isTrue();
    }

    @Test
    void dob_today_isRejected() {
        // @Past requires strictly before today — a newborn's dob is still "today", so
        // this is the exact boundary a caller could plausibly hit; it must not slip
        // through as "in the past".
        PatientRequest request = valid();
        request.setDob(LocalDate.now());
        assertThat(hasViolationOn(request, "dob")).isTrue();
    }

    @Test
    void dob_tomorrow_isRejected() {
        PatientRequest request = valid();
        request.setDob(LocalDate.now().plusDays(1));
        assertThat(hasViolationOn(request, "dob")).isTrue();
    }

    @Test
    void dob_farFuture_isRejected() {
        PatientRequest request = valid();
        request.setDob(LocalDate.now().plusYears(50));
        assertThat(hasViolationOn(request, "dob")).isTrue();
    }

    @Test
    void dob_yesterday_isAccepted() {
        PatientRequest request = valid();
        request.setDob(LocalDate.now().minusDays(1));
        assertThat(hasViolationOn(request, "dob")).isFalse();
    }

    @Test
    void dob_farPast_isAccepted() {
        PatientRequest request = valid();
        request.setDob(LocalDate.of(1900, 1, 1));
        assertThat(hasViolationOn(request, "dob")).isFalse();
    }

    // ── other fields ─────────────────────────────────────────────────────────

    @Test
    void blankFirstOrLastName_isRejected() {
        PatientRequest request = valid();
        request.setFirstName("");
        assertThat(hasViolationOn(request, "firstName")).isTrue();

        request = valid();
        request.setLastName(" ");
        assertThat(hasViolationOn(request, "lastName")).isTrue();
    }

    @Test
    void nameWithDigitsOrSymbols_isRejected() {
        for (String bad : new String[]{"Alice2", "Alice!", "Alice_Doe", "Alice@Doe"}) {
            PatientRequest request = valid();
            request.setFirstName(bad);
            assertThat(hasViolationOn(request, "firstName")).as("first name '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void hyphenatedAndApostropheNames_areAccepted() {
        PatientRequest request = valid();
        request.setFirstName("Mary-Jane");
        request.setLastName("O'Brien");
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void invalidGender_isRejected() {
        for (String bad : new String[]{"Male", "X", "m1", ""}) {
            PatientRequest request = valid();
            request.setGender(bad);
            assertThat(hasViolationOn(request, "gender")).as("gender '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void genderIsCaseInsensitive() {
        for (String ok : new String[]{"m", "M", "f", "F", "other", "OTHER", "Other"}) {
            PatientRequest request = valid();
            request.setGender(ok);
            assertThat(hasViolationOn(request, "gender")).as("gender '%s' should be accepted", ok).isFalse();
        }
    }

    @Test
    void malformedPhone_isRejected() {
        for (String bad : new String[]{"123", "abcdefg", "123-456-7890", "12345678901234567"}) {
            PatientRequest request = valid();
            request.setPhone(bad);
            assertThat(hasViolationOn(request, "phone")).as("phone '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void phoneWithLeadingPlus_isAccepted() {
        PatientRequest request = valid();
        request.setPhone("+15551234567");
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void malformedEmail_isRejected() {
        PatientRequest request = valid();
        request.setEmail("not-an-email");
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    void invalidStatus_isRejected() {
        PatientRequest request = valid();
        request.setStatus("archived");
        assertThat(hasViolationOn(request, "status")).isTrue();
    }

    @Test
    void statusIsOptional() {
        PatientRequest request = valid();
        request.setStatus(null);
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void addressOver255Characters_isRejected() {
        PatientRequest request = valid();
        request.setAddress("a".repeat(256));
        assertThat(hasViolationOn(request, "address")).isTrue();
    }
}
