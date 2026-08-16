package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.utils.filters.PagedRawResult;
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
 * Exercises {@link FindUserDataAspect} branches with no production caller today:
 * real callers ({@code UserService.findUsersPage}/{@code AppointmentService}/
 * {@code DoctorService}/{@code PatientService}) only ever use domain="user"/
 * "appointment"/"doctor"/"patient", always paginated, and never set
 * {@code userId()}/{@code username()} on the annotation itself. This test-local bean
 * covers: the "role"/"permission" domains (see CLAUDE.md — unexercised by a real
 * caller, double-check-before-relying-on-them territory), the non-paginated branch,
 * the userId()/username() concatenation branches, and the unknown-domain error path.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FindUserDataAspectTest {

    @Autowired
    private TestFindUserDataBean bean;

    @Test
    void roleDomain_paginated_runsRealQuery() {
        // Seeded roles always exist and always hold at least one user (see DataSeeder).
        PagedRawResult result = bean.findRolesPaginated(0, 10);
        assertThat(result.rows()).isNotNull();
        assertThat(result.total()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void permissionDomain_paginated_runsRealQuery() {
        PagedRawResult result = bean.findPermissionsPaginated(0, 10);
        assertThat(result.rows()).isNotNull();
        assertThat(result.total()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void nonPaginatedCall_runsPlainListQuery() {
        List<?> rows = bean.findUsersNonPaginated();
        assertThat(rows).isNotNull();
    }

    @Test
    void userIdAndUsernameAttributes_areConcatenatedIntoWhereClause() {
        // A nonexistent id/username still runs a valid query — just returns no rows.
        PagedRawResult result = bean.findByUserId(0, 10);
        assertThat(result.rows()).isEmpty();

        PagedRawResult result2 = bean.findByUsername(0, 10);
        assertThat(result2.rows()).isEmpty();
    }

    @Test
    void unknownDomain_throwsIllegalState() {
        assertThrows(IllegalStateException.class, bean::findUnknownDomain);
    }

    @TestConfiguration
    static class Config {
        @Bean
        TestFindUserDataBean testFindUserDataBean() {
            return new TestFindUserDataBean();
        }
    }

    /** Test-only stand-in for a real caller — see class Javadoc. */
    public static class TestFindUserDataBean {
        @FindUserData(domain = "role")
        public PagedRawResult findRolesPaginated(int page, int size) {
            throw new IllegalStateException("FindUserDataAspect did not intercept this call");
        }

        @FindUserData(domain = "permission")
        public PagedRawResult findPermissionsPaginated(int page, int size) {
            throw new IllegalStateException("FindUserDataAspect did not intercept this call");
        }

        @FindUserData(domain = "user")
        public List<?> findUsersNonPaginated() {
            throw new IllegalStateException("FindUserDataAspect did not intercept this call");
        }

        @FindUserData(domain = "user", userId = "nonexistent-user-id")
        public PagedRawResult findByUserId(int page, int size) {
            throw new IllegalStateException("FindUserDataAspect did not intercept this call");
        }

        @FindUserData(domain = "user", username = "nonexistent-username")
        public PagedRawResult findByUsername(int page, int size) {
            throw new IllegalStateException("FindUserDataAspect did not intercept this call");
        }

        @FindUserData(domain = "bogus-domain")
        public List<?> findUnknownDomain() {
            throw new IllegalStateException("FindUserDataAspect did not intercept this call");
        }
    }
}
