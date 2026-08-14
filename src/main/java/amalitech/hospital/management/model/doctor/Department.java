package amalitech.hospital.management.model.doctor;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity model for the `departments` table.
 * Many-to-Many relationship with doctors.
 */
@Data
@Entity
@Table(name = "departments",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"name"}),
                @UniqueConstraint(columnNames = {"phone"})
        })
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "department_id", updatable = false, nullable = false)
    private String departmentId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ── Cardinality ────────────────────────────────────────────────
    @ManyToMany(mappedBy = "departments")
    private List<Doctor> doctors;
}
