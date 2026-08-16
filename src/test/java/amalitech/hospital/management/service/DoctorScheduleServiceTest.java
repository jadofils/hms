package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.doctor.DoctorScheduleRequest;
import amalitech.hospital.management.dto.doctor.DoctorScheduleResponse;
import amalitech.hospital.management.enums.ScheduleDay;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.Doctor;
import amalitech.hospital.management.model.doctor.DoctorSchedule;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import amalitech.hospital.management.repository.doctor.DoctorScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorScheduleServiceTest {

    @Mock private DoctorScheduleRepository doctorScheduleRepository;
    @Mock private DoctorRepository doctorRepository;

    private DoctorScheduleService scheduleService;

    private Doctor existingDoctor;
    private DoctorSchedule existingSchedule;

    @BeforeEach
    void setUp() {
        scheduleService = new DoctorScheduleService(doctorScheduleRepository, doctorRepository);

        existingDoctor = new Doctor();
        existingDoctor.setDoctorId("doctor-1");

        existingSchedule = new DoctorSchedule();
        existingSchedule.setScheduleId("schedule-1");
        existingSchedule.setDoctorId("doctor-1");
        existingSchedule.setDayOfWeek(ScheduleDay.MON);
        existingSchedule.setStartTime(LocalTime.of(9, 0));
        existingSchedule.setEndTime(LocalTime.of(17, 0));
        existingSchedule.setIsAvailable(true);
    }

    // ── getSchedules ─────────────────────────────────────────────────────────

    @Test
    void getSchedules_throwsNotFound_whenDoctorAbsent() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedules("doctor-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getSchedules_returnsMappedResponses() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorScheduleRepository.findByDoctorIdAndDeletedAtIsNull("doctor-1"))
                .thenReturn(List.of(existingSchedule));

        List<DoctorScheduleResponse> result = scheduleService.getSchedules("doctor-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDayOfWeek()).isEqualTo("Mon");
    }

    // ── createSchedule ───────────────────────────────────────────────────────

    @Test
    void createSchedule_throwsNotFound_whenDoctorAbsent() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.empty());
        DoctorScheduleRequest request = requestFor("Mon", LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThatThrownBy(() -> scheduleService.createSchedule("doctor-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createSchedule_throwsBadRequest_whenDayInvalid() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        DoctorScheduleRequest request = requestFor("Bogusday", LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThatThrownBy(() -> scheduleService.createSchedule("doctor-1", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSchedule_throwsBadRequest_whenEndTimeNotAfterStartTime() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        DoctorScheduleRequest request = requestFor("Mon", LocalTime.of(17, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> scheduleService.createSchedule("doctor-1", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSchedule_savesSuccessfully() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorScheduleRepository.save(any(DoctorSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
        DoctorScheduleRequest request = requestFor("Mon", LocalTime.of(9, 0), LocalTime.of(17, 0));

        DoctorScheduleResponse response = scheduleService.createSchedule("doctor-1", request);

        assertThat(response.getDoctorId()).isEqualTo("doctor-1");
        assertThat(response.getDayOfWeek()).isEqualTo("Mon");
        assertThat(response.getIsAvailable()).isTrue();
    }

    // ── updateSchedule / deleteSchedule ──────────────────────────────────────

    @Test
    void updateSchedule_throwsNotFound_whenScheduleBelongsToDifferentDoctor() {
        when(doctorScheduleRepository.findById("schedule-1")).thenReturn(Optional.of(existingSchedule));
        DoctorScheduleRequest request = requestFor("Tue", LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThatThrownBy(() -> scheduleService.updateSchedule("other-doctor", "schedule-1", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateSchedule_updatesFields() {
        when(doctorScheduleRepository.findById("schedule-1")).thenReturn(Optional.of(existingSchedule));
        when(doctorScheduleRepository.save(any(DoctorSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
        DoctorScheduleRequest request = requestFor("Tue", LocalTime.of(10, 0), LocalTime.of(18, 0));

        DoctorScheduleResponse response = scheduleService.updateSchedule("doctor-1", "schedule-1", request);

        assertThat(response.getDayOfWeek()).isEqualTo("Tue");
        assertThat(existingSchedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void deleteSchedule_setsDeletedAt() {
        when(doctorScheduleRepository.findById("schedule-1")).thenReturn(Optional.of(existingSchedule));
        when(doctorScheduleRepository.save(any(DoctorSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.deleteSchedule("doctor-1", "schedule-1");

        assertThat(existingSchedule.getDeletedAt()).isNotNull();
    }

    // ── isDoctorAvailable ────────────────────────────────────────────────────

    @Test
    void isDoctorAvailable_returnsTrue_whenTimeFallsWithinAnAvailableBlock() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorScheduleRepository.findByDoctorIdAndDeletedAtIsNull("doctor-1"))
                .thenReturn(List.of(existingSchedule));

        assertThat(scheduleService.isDoctorAvailable("doctor-1", "Mon", "10:00")).isTrue();
    }

    @Test
    void isDoctorAvailable_returnsFalse_whenTimeOutsideAnyBlock() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorScheduleRepository.findByDoctorIdAndDeletedAtIsNull("doctor-1"))
                .thenReturn(List.of(existingSchedule));

        assertThat(scheduleService.isDoctorAvailable("doctor-1", "Mon", "20:00")).isFalse();
    }

    @Test
    void isDoctorAvailable_returnsFalse_whenBlockMarkedUnavailable() {
        existingSchedule.setIsAvailable(false);
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));
        when(doctorScheduleRepository.findByDoctorIdAndDeletedAtIsNull("doctor-1"))
                .thenReturn(List.of(existingSchedule));

        assertThat(scheduleService.isDoctorAvailable("doctor-1", "Mon", "10:00")).isFalse();
    }

    @Test
    void isDoctorAvailable_throwsBadRequest_whenTimeMalformed() {
        when(doctorRepository.findById("doctor-1")).thenReturn(Optional.of(existingDoctor));

        assertThatThrownBy(() -> scheduleService.isDoctorAvailable("doctor-1", "Mon", "not-a-time"))
                .isInstanceOf(BadRequestException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DoctorScheduleRequest requestFor(String day, LocalTime start, LocalTime end) {
        DoctorScheduleRequest request = new DoctorScheduleRequest();
        request.setDayOfWeek(day);
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }
}
