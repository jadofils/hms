package amalitech.hospital.management.dto.doctor;

import lombok.Data;

import java.time.LocalTime;

@Data
public class DoctorScheduleResponse {
    private String scheduleId;
    private String doctorId;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isAvailable;
}
