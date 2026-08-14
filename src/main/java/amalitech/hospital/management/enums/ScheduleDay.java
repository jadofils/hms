package amalitech.hospital.management.enums;

/**
 * Maps the `doctor_schedules.day_of_week` CHECK constraint values.
 * Named ScheduleDay to avoid shadowing java.time.DayOfWeek.
 */
public enum ScheduleDay {
    MON("Mon", "Monday"),
    TUE("Tue", "Tuesday"),
    WED("Wed", "Wednesday"),
    THU("Thu", "Thursday"),
    FRI("Fri", "Friday"),
    SAT("Sat", "Saturday"),
    SUN("Sun", "Sunday");

    private final String dbValue;
    private final String label;

    ScheduleDay(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label   = label;
    }

    public String getDbValue() { return dbValue; }
    public String getLabel()   { return label; }

    public static ScheduleDay fromDbValue(String value) {
        for (ScheduleDay d : values()) {
            if (d.dbValue.equalsIgnoreCase(value)) return d;
        }
        throw new IllegalArgumentException("Unknown ScheduleDay: " + value);
    }

    @Override public String toString() { return dbValue; }
}