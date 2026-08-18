package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.log.SystemLogResponse;
import amalitech.hospital.management.service.SystemLogService;
import amalitech.hospital.management.utils.GraphQlPaging;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link SystemLogService} — see {@code UserResolver}'s Javadoc
 * for the shared reasoning (same service layer as REST). Query-only: no mutations exist
 * for the same reason {@code SystemLogController} has no POST/PUT/DELETE mapping — see
 * {@code SystemLogService}'s own Javadoc.
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class SystemLogResolver {

    private final SystemLogService systemLogService;

    @QueryMapping
    public List<SystemLogResponse> systemLogs(@Argument int page, @Argument int size, @Argument String sort,
            @Argument String logLevel, @Argument String source) {
        return systemLogService.getSystemLogs(GraphQlPaging.of(page, size, sort), logLevel, source).getContent();
    }

    @QueryMapping
    public SystemLogResponse systemLog(@Argument String logId) {
        return systemLogService.getSystemLog(logId);
    }
}
