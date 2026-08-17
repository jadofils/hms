package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.doctor.DoctorScheduleRequest;
import amalitech.hospital.management.dto.doctor.DoctorScheduleResponse;
import amalitech.hospital.management.service.DoctorScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link DoctorScheduleService} — see {@code UserResolver}'s
 * Javadoc for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class DoctorScheduleResolver {

    private final DoctorScheduleService doctorScheduleService;

    @QueryMapping
    public List<DoctorScheduleResponse> doctorSchedules(@Argument String doctorId) {
        return doctorScheduleService.getSchedules(doctorId);
    }

    @QueryMapping
    public boolean doctorAvailability(@Argument String doctorId, @Argument String day, @Argument String time) {
        return doctorScheduleService.isDoctorAvailable(doctorId, day, time);
    }

    @MutationMapping
    public DoctorScheduleResponse createDoctorSchedule(@Argument String doctorId, @Argument @Valid DoctorScheduleRequest input) {
        return doctorScheduleService.createSchedule(doctorId, input);
    }

    @MutationMapping
    public DoctorScheduleResponse updateDoctorSchedule(@Argument String doctorId, @Argument String scheduleId,
            @Argument @Valid DoctorScheduleRequest input) {
        return doctorScheduleService.updateSchedule(doctorId, scheduleId, input);
    }

    @MutationMapping
    public boolean deleteDoctorSchedule(@Argument String doctorId, @Argument String scheduleId) {
        doctorScheduleService.deleteSchedule(doctorId, scheduleId);
        return true;
    }
}
