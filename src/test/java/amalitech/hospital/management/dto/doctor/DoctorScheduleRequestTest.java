package amalitech.hospital.management.dto.doctor;

import amalitech.hospital.management.dto.ValidationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorScheduleRequestTest extends ValidationTestBase {

    private static DoctorScheduleRequest valid() {
        DoctorScheduleRequest request = new DoctorScheduleRequest();
        request.setDayOfWeek("Mon");
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(17, 0));
        return request;
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    @Test
    void dayOfWeek_everyValidAbbreviation_isAccepted() {
        for (String day : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "mon", "MON"}) {
            DoctorScheduleRequest request = valid();
            request.setDayOfWeek(day);
            assertThat(hasViolationOn(request, "dayOfWeek")).as("day '%s' should be accepted", day).isFalse();
        }
    }

    @Test
    void dayOfWeek_fullNameOrInvalid_isRejected() {
        for (String bad : new String[]{"Monday", "Mo", "Funday", ""}) {
            DoctorScheduleRequest request = valid();
            request.setDayOfWeek(bad);
            assertThat(hasViolationOn(request, "dayOfWeek")).as("day '%s' should be rejected", bad).isTrue();
        }
    }

    @Test
    void startOrEndTime_null_isRejected() {
        DoctorScheduleRequest request = valid();
        request.setStartTime(null);
        assertThat(hasViolationOn(request, "startTime")).isTrue();

        request = valid();
        request.setEndTime(null);
        assertThat(hasViolationOn(request, "endTime")).isTrue();
    }

    @Test
    void isAvailableIsOptional() {
        DoctorScheduleRequest request = valid();
        request.setIsAvailable(null);
        assertThat(validate(request)).isEmpty();
    }
}
