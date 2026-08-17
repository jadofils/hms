package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.doctor.DoctorScheduleResponse;
import amalitech.hospital.management.service.DoctorScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link DoctorScheduleResolver} — see {@code UserResolverTest}'s Javadoc
 *  for the shared reasoning. Also exercises the hand-rolled {@code LocalTime} scalar
 *  registered by {@link GraphQlConfig} — {@code startTime}/{@code endTime} round-trip as
 *  plain "HH:mm:ss" strings. */
@GraphQlTest(DoctorScheduleResolver.class)
@Import(GraphQlConfig.class)
class DoctorScheduleResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private DoctorScheduleService doctorScheduleService;

    private DoctorScheduleResponse existingSchedule() {
        DoctorScheduleResponse response = new DoctorScheduleResponse();
        response.setScheduleId("sched-1");
        response.setDoctorId("doctor-1");
        response.setDayOfWeek("Mon");
        response.setStartTime(LocalTime.of(9, 0));
        response.setEndTime(LocalTime.of(17, 0));
        response.setIsAvailable(true);
        return response;
    }

    @Test
    void doctorSchedules_returnsMappedResponses() {
        when(doctorScheduleService.getSchedules("doctor-1")).thenReturn(List.of(existingSchedule()));

        graphQlTester.document("{ doctorSchedules(doctorId: \"doctor-1\") { scheduleId startTime endTime } }")
                .execute()
                .path("doctorSchedules[0].startTime").entity(String.class).isEqualTo("09:00")
                .path("doctorSchedules[0].endTime").entity(String.class).isEqualTo("17:00");
    }

    @Test
    void doctorAvailability_delegatesToService() {
        when(doctorScheduleService.isDoctorAvailable("doctor-1", "Mon", "10:00")).thenReturn(true);

        graphQlTester.document("{ doctorAvailability(doctorId: \"doctor-1\", day: \"Mon\", time: \"10:00\") }")
                .execute()
                .path("doctorAvailability").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void createDoctorSchedule_delegatesToService() {
        when(doctorScheduleService.createSchedule(eq("doctor-1"), any())).thenReturn(existingSchedule());

        graphQlTester.document(
                        "mutation { createDoctorSchedule(doctorId: \"doctor-1\", input: { dayOfWeek: \"Mon\", startTime: \"09:00:00\", endTime: \"17:00:00\" }) { scheduleId } }")
                .execute()
                .path("createDoctorSchedule.scheduleId").entity(String.class).isEqualTo("sched-1");
    }

    @Test
    void deleteDoctorSchedule_returnsTrue() {
        graphQlTester.document("mutation { deleteDoctorSchedule(doctorId: \"doctor-1\", scheduleId: \"sched-1\") }")
                .execute()
                .path("deleteDoctorSchedule").entity(Boolean.class).isEqualTo(true);

        verify(doctorScheduleService).deleteSchedule("doctor-1", "sched-1");
    }
}
