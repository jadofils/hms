package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.log.SystemLogResponse;
import amalitech.hospital.management.enums.SystemLogLevel;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.logs.SystemLog;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import amalitech.hospital.management.utils.PageableDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

/**
 * Read-only access to {@link SystemLog} — the operational trail
 * {@code LoggingAspect}/{@code SystemLogWriter} already write on every service-method
 * failure (see those classes), which previously had no repository/service/controller/
 * resolver layer of its own at all: rows were being persisted with no way to ever read
 * them back through the API. There is deliberately no create/update/delete here —
 * {@code SystemLogWriter.record} is the only writer, and a log entry is never edited
 * once written (append-only, same as {@code AuditLog} would be if anything wrote to it —
 * see that entity's own Javadoc).
 *
 * Single-item lookups are cached in Redis under the "systemLogs" cache — safe here
 * specifically because a log row never changes after creation, so there's no write path
 * that could ever leave a stale cache entry behind (contrast every other {@code @Cacheable}
 * lookup in this codebase, which pairs with a {@code @CachePut}/{@code @CacheEvict} on its
 * own update/delete).
 */
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    /**
     * Both filters are optional and independently combinable. {@code logLevel} is
     * validated against {@link SystemLogLevel}'s own allowed values before reaching a
     * query, the same safety principle {@code InvoiceService.getInvoices}'
     * {@code paymentStatus} filter uses. {@code source} is a case-insensitive contains
     * match (see {@code SystemLogRepository}'s own Javadoc for why) — e.g.
     * {@code source=RoleService} surfaces every failure logged from that class,
     * regardless of exact method name.
     */
    public PagedModel<SystemLogResponse> getSystemLogs(Pageable pageable, String logLevel, String source) {
        // Defaults to createdAt DESC (matching this endpoint's own Swagger sort
        // example) when the caller sends no ?sort= at all — covers all four
        // branches below. See PageableDefaults' own Javadoc.
        Pageable sorted = PageableDefaults.withDefaultSort(pageable, "createdAt", Sort.Direction.DESC);
        boolean hasLevel = logLevel != null && !logLevel.isBlank();
        boolean hasSource = source != null && !source.isBlank();

        if (hasLevel && hasSource) {
            String validated = validateLevel(logLevel);
            return new PagedModel<>(systemLogRepository
                    .findByLogLevelAndSourceContainingIgnoreCase(validated, source, sorted).map(this::toResponse));
        }
        if (hasLevel) {
            String validated = validateLevel(logLevel);
            return new PagedModel<>(systemLogRepository.findByLogLevel(validated, sorted).map(this::toResponse));
        }
        if (hasSource) {
            return new PagedModel<>(
                    systemLogRepository.findBySourceContainingIgnoreCase(source, sorted).map(this::toResponse));
        }
        return new PagedModel<>(systemLogRepository.findAll(sorted).map(this::toResponse));
    }

    @Cacheable(value = "systemLogs", key = "#logId")
    public SystemLogResponse getSystemLog(String logId) {
        return toResponse(systemLogRepository.findById(logId)
                .orElseThrow(() -> new NotFoundException("System log not found: " + logId)));
    }

    /** The DTO/GraphQL-argument layer has no {@code @Pattern} constraint of its own
     *  (unlike {@code InvoiceRequest.paymentStatus}) since this is a query filter, not a
     *  request body field — this is the only validation {@code logLevel} gets. */
    private String validateLevel(String logLevel) {
        try {
            return SystemLogLevel.fromDbValue(logLevel).getDbValue();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private SystemLogResponse toResponse(SystemLog log) {
        SystemLogResponse response = new SystemLogResponse();
        response.setLogId(log.getLogId());
        response.setLogLevel(log.getLogLevel());
        response.setSource(log.getSource());
        response.setMessage(log.getMessage());
        response.setUserId(log.getUser() != null ? log.getUser().getUserId() : null);
        response.setCreatedAt(log.getCreatedAt());
        return response;
    }
}
