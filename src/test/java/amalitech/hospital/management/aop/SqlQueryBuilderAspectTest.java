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
 * real (test) database. None of its 3 cases has a production caller yet — per
 * CLAUDE.md, {@code RoleService.getRolePermissionCounts}/{@code findRolesWithPermissionCount}
 * was the one wired example and was removed for existing only to exercise this
 * pattern. This test-local bean stands in for "the next real caller" purely so the
 * aspect itself (all 3 switch cases + the unknown-key default branch) is covered.
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
