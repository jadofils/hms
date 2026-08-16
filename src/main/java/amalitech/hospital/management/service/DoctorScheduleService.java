package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.doctor.DoctorScheduleRequest;
import amalitech.hospital.management.dto.doctor.DoctorScheduleResponse;
import amalitech.hospital.management.enums.ScheduleDay;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.doctor.DoctorSchedule;
import amalitech.hospital.management.repository.doctor.DoctorRepository;
import amalitech.hospital.management.repository.doctor.DoctorScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Doctor weekly recurring availability blocks ("the scheduler"). Scoped under a
 * {@code doctorId} — every operation checks the doctor exists first.
 *
 * No generated discrete bookable slots yet — just the recurring day/time blocks and an
 * availability check ({@link #isDoctorAvailable}) a frontend can call before booking,
 * independent of {@code Appointment} actually existing yet.
 */
@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorRepository doctorRepository;

    public List<DoctorScheduleResponse> getSchedules(String doctorId) {
        requireDoctorExists(doctorId);
        return doctorScheduleRepository.findByDoctorIdAndDeletedAtIsNull(doctorId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DoctorScheduleResponse createSchedule(String doctorId, DoctorScheduleRequest request) {
        requireDoctorExists(doctorId);
        ScheduleDay day = validateDay(request.getDayOfWeek());
        validateTimeRange(request.getStartTime(), request.getEndTime());

        LocalDateTime now = LocalDateTime.now();
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctorId(doctorId);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setIsAvailable(request.getIsAvailable() == null || request.getIsAvailable());
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        return toResponse(doctorScheduleRepository.save(schedule));
    }

    @Transactional
    public DoctorScheduleResponse updateSchedule(String doctorId, String scheduleId, DoctorScheduleRequest request) {
        DoctorSchedule schedule = findScheduleOrThrow(doctorId, scheduleId);
        ScheduleDay day = validateDay(request.getDayOfWeek());
        validateTimeRange(request.getStartTime(), request.getEndTime());

        schedule.setDayOfWeek(day);
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setIsAvailable(request.getIsAvailable() == null || request.getIsAvailable());
        schedule.setUpdatedAt(LocalDateTime.now());
        return toResponse(doctorScheduleRepository.save(schedule));
    }

    @Transactional
    public void deleteSchedule(String doctorId, String scheduleId) {
        DoctorSchedule schedule = findScheduleOrThrow(doctorId, scheduleId);
        schedule.setDeletedAt(LocalDateTime.now());
        doctorScheduleRepository.save(schedule);
    }

    /** Real caller: an availability-check endpoint a frontend can hit before booking. */
    public boolean isDoctorAvailable(String doctorId, String day, String time) {
        requireDoctorExists(doctorId);
        ScheduleDay scheduleDay = validateDay(day);
        LocalTime queryTime;
        try {
            queryTime = LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Time must be in HH:mm format: " + time);
        }

        return doctorScheduleRepository.findByDoctorIdAndDeletedAtIsNull(doctorId).stream()
                .filter(s -> s.getDayOfWeek() == scheduleDay)
                .filter(s -> Boolean.TRUE.equals(s.getIsAvailable()))
                .anyMatch(s -> !queryTime.isBefore(s.getStartTime()) && !queryTime.isAfter(s.getEndTime()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireDoctorExists(String doctorId) {
        boolean exists = doctorRepository.findById(doctorId)
                .filter(d -> d.getDeletedAt() == null)
                .isPresent();
        if (!exists) {
            throw new NotFoundException("Doctor not found: " + doctorId);
        }
    }

    private DoctorSchedule findScheduleOrThrow(String doctorId, String scheduleId) {
        DoctorSchedule schedule = doctorScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("Schedule not found: " + scheduleId));
        if (schedule.getDeletedAt() != null || !schedule.getDoctorId().equals(doctorId)) {
            throw new NotFoundException("Schedule not found: " + scheduleId);
        }
        return schedule;
    }

    /** The DTO's own {@code @Pattern} already constrains this to an allowed value, so
     *  {@link ScheduleDay#fromDbValue} should never actually throw here — this is defense
     *  in depth, not the primary validation path. */
    private ScheduleDay validateDay(String day) {
        try {
            return ScheduleDay.fromDbValue(day);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BadRequestException("End time must be after start time");
        }
    }

    private DoctorScheduleResponse toResponse(DoctorSchedule schedule) {
        DoctorScheduleResponse response = new DoctorScheduleResponse();
        response.setScheduleId(schedule.getScheduleId());
        response.setDoctorId(schedule.getDoctorId());
        response.setDayOfWeek(schedule.getDayOfWeek().getDbValue());
        response.setStartTime(schedule.getStartTime());
        response.setEndTime(schedule.getEndTime());
        response.setIsAvailable(schedule.getIsAvailable());
        return response;
    }
}
