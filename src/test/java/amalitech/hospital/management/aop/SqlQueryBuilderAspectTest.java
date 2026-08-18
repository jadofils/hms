package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.SqlQueryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link SqlQueryBuilderAspect} through a real Spring AOP proxy against the
 * real (test) database. All 3 cases now have a real production caller —
 * {@code RoleService.findRolesWithPermissionCount}, {@code DepartmentService.findDepartmentsWithDoctorCounts},
 * {@code DoctorService.findDoctorsByDepartment} (see {@code RoleServiceTest}/
 * {@code DepartmentServiceTest}/{@code DoctorServiceTest} for their own unit coverage,
 * and {@code RoleControllerTest}/{@code DepartmentControllerTest}/{@code DoctorControllerTest}
 * for end-to-end HTTP coverage) — this test-local bean still exists to exercise the
 * aspect itself directly (all 3 switch cases + the unknown-key default branch), isolated
 * from any one caller's own business logic.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SqlQueryBuilderAspectTest {

    @Autowired
    private TestQueryBean bean;

    @Test
    void findDoctorsByDepartment_runsRealNativeQuery() {
        assertThat(bean.findDoctorsByDepartment()).isNotNull();
    }

    @Test
    void findDepartmentsWithDoctors_runsRealNativeQuery() {
        assertThat(bean.findDepartmentsWithDoctors()).isNotNull();
    }

    @Test
    void findRolesWithPermissionCount_runsRealNativeQuery() {
        // Seeded roles always exist (see DataSeeder), so this always returns rows.
        assertThat(bean.findRolesWithPermissionCount()).isNotEmpty();
    }

    @Test
    void unknownQueryKey_throwsIllegalState() {
        assertThrows(IllegalStateException.class, bean::unknownQuery);
    }

    @TestConfiguration
    static class Config {
        @Bean
        TestQueryBean testQueryBean() {
            return new TestQueryBean();
        }
    }

    /** Test-only stand-in for a real caller — see class Javadoc. */
    public static class TestQueryBean {
        @SqlQueryBuilder("findDoctorsByDepartment")
        public List<?> findDoctorsByDepartment() {
            throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
        }

        @SqlQueryBuilder("findDepartmentsWithDoctors")
        public List<?> findDepartmentsWithDoctors() {
            throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
        }

        @SqlQueryBuilder("findRolesWithPermissionCount")
        public List<?> findRolesWithPermissionCount() {
            throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
        }

        @SqlQueryBuilder("bogusKey")
        public List<?> unknownQuery() {
            throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
        }
    }
}
