package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.log.SystemLogResponse;
import amalitech.hospital.management.service.SystemLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link SystemLogResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. Query-only: no mutations exist for this resolver. */
@GraphQlTest(SystemLogResolver.class)
@Import(GraphQlConfig.class)
class SystemLogResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private SystemLogService systemLogService;

    private SystemLogResponse existingLog() {
        SystemLogResponse response = new SystemLogResponse();
        response.setLogId("log-1");
        response.setLogLevel("ERROR");
        response.setSource("amalitech.hospital.management.service.RoleService.grantPermission");
        response.setMessage("Role already has this permission");
        return response;
    }

    @Test
    void systemLog_returnsMappedResponse() {
        when(systemLogService.getSystemLog("log-1")).thenReturn(existingLog());

        graphQlTester.document("{ systemLog(logId: \"log-1\") { logLevel source message } }")
                .execute()
                .path("systemLog.logLevel").entity(String.class).isEqualTo("ERROR")
                .path("systemLog.message").entity(String.class).isEqualTo("Role already has this permission");

        verify(systemLogService).getSystemLog("log-1");
    }

    @Test
    void systemLogs_delegatesPagingAndFilters() {
        when(systemLogService.getSystemLogs(any(), eq("ERROR"), eq("RoleService")))
                .thenReturn(new PagedModel<>(new PageImpl<>(List.of(existingLog()))));

        graphQlTester.document(
                        "{ systemLogs(page: 0, size: 20, logLevel: \"ERROR\", source: \"RoleService\") { logId logLevel } }")
                .execute()
                .path("systemLogs[0].logId").entity(String.class).isEqualTo("log-1");

        verify(systemLogService).getSystemLogs(any(), eq("ERROR"), eq("RoleService"));
    }

    @Test
    void systemLogs_appliesRequestedSort() {
        when(systemLogService.getSystemLogs(any(), any(), any()))
                .thenReturn(new PagedModel<>(new PageImpl<>(List.of(existingLog()))));

        graphQlTester.document("{ systemLogs(page: 0, size: 20, sort: \"createdAt,desc\") { logId } }")
                .execute()
                .path("systemLogs[0].logId").entity(String.class).isEqualTo("log-1");

        verify(systemLogService).getSystemLogs(
                eq(PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))),
                any(), any());
    }
}
