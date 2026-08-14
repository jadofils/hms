package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import amalitech.hospital.management.utils.filters.QueryBuilder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds and runs a native SQL lookup for the domain named in {@code @FindUserData},
 * replacing the annotated method's own body entirely.
 *
 * The {@code domain()}/{@code userId()}/{@code username()} annotation attributes are
 * fixed at compile time — Java annotations can't carry a value the caller only knows at
 * request time (e.g. "page 3", "sort by username"). So when the annotated method itself
 * declares {@code (int page, int size)} — optionally followed by {@code (String sortBy,
 * String sortDir)} — this aspect reads those off the *method's actual runtime
 * arguments* (via {@link ProceedingJoinPoint#getArgs()}) instead of the annotation — the
 * same trick {@code AlgorithmAspect} uses — and returns a {@link PagedRawResult} (page
 * of rows + total count) rather than the plain row list.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class FindUserDataAspect {

    private final EntityManager entityManager;

    @Around("@annotation(findUserData)")
    public Object executeFindUserData(ProceedingJoinPoint pjp, FindUserData findUserData) throws Throwable {
        Object[] args = pjp.getArgs();
        boolean paginated = args.length >= 2 && args[0] instanceof Integer && args[1] instanceof Integer;

        if (!paginated) {
            QueryBuilder builder = buildQuery(findUserData, selectColumnsFor(findUserData.domain()));
            return entityManager.createNativeQuery(builder.build()).getResultList();
        }

        int page = (Integer) args[0];
        int size = (Integer) args[1];
        String sortBy = args.length > 2 && args[2] instanceof String s ? s : null;
        String sortDir = args.length > 3 && args[3] instanceof String s ? s : null;

        // A frontend-chosen sort column is only ever resolved against this domain's own
        // SELECT list (never concatenated into the query raw) — anything unrecognized
        // falls back to the first selected column, so paginated results stay
        // deterministic page-to-page either way (Postgres gives no ordering guarantee
        // at all without an ORDER BY, sort request or not).
        String orderColumn = resolveSortColumn(findUserData.domain(), sortBy);
        QueryBuilder.SortDir direction = "DESC".equalsIgnoreCase(sortDir) ? QueryBuilder.SortDir.DESC : QueryBuilder.SortDir.ASC;

        QueryBuilder rowsBuilder = buildQuery(findUserData, selectColumnsFor(findUserData.domain()))
                .orderBy(orderColumn, direction)
                .limit(size)
                .offset(page * size);
        QueryBuilder countBuilder = buildQuery(findUserData, "COUNT(*)");

        List<?> rows = entityManager.createNativeQuery(rowsBuilder.build()).getResultList();
        Number total = (Number) entityManager.createNativeQuery(countBuilder.build()).getSingleResult();
        return new PagedRawResult(rows, total.longValue());
    }

    private String[] selectColumnsFor(String domain) {
        return switch (domain) {
            case "user" -> new String[]{"u.user_id", "u.username", "u.email", "u.is_active"};
            case "role" -> new String[]{"r.role_id", "r.role_name"};
            case "permission" -> new String[]{"p.permission_id", "p.resource", "p.action"};
            case "appointment" -> new String[]{"a.appointment_id", "a.appointment_date", "a.status"};
            case "doctor" -> new String[]{"d.doctor_id", "d.name", "dep.name AS department"};
            default -> throw new IllegalStateException("Unknown domain: " + domain);
        };
    }

    /**
     * Maps the client-facing name of every column {@code selectColumnsFor} exposes for
     * this domain (e.g. {@code "username"}, or {@code "department"} for an aliased
     * expression) to the actual SQL expression to order by (e.g. {@code "u.username"}).
     * This — not a separate hand-maintained list — is the whitelist a requested sort
     * column is checked against, so a sortable column can never drift out of sync with
     * what the query actually selects. Keyed by {@link #normalize}d name so a frontend
     * matching its JSON DTO field names (e.g. {@code "isActive"}) lines up with the
     * underlying snake_case DB column ({@code "is_active"}) without the caller needing
     * to know which convention the column itself uses.
     */
    private Map<String, String> sortableColumnsFor(String domain) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (String column : selectColumnsFor(domain)) {
            String[] parts = column.split("(?i)\\s+AS\\s+", 2);
            String expression = parts[0].trim();
            String clientName = parts.length == 2
                    ? parts[1].trim()
                    : expression.substring(expression.indexOf('.') + 1);
            columns.put(normalize(clientName), expression);
        }
        return columns;
    }

    /** Resolves a caller-supplied sort column against the domain's whitelist, falling
     *  back to the first selected column when it's missing, blank, or not recognized. */
    private String resolveSortColumn(String domain, String sortBy) {
        Map<String, String> sortable = sortableColumnsFor(domain);
        String resolved = sortBy == null ? null : sortable.get(normalize(sortBy));
        return resolved != null ? resolved : sortable.values().iterator().next();
    }

    /** Lowercases and strips underscores so {@code "isActive"}/{@code "is_active"}/
     *  {@code "IS_ACTIVE"} all compare equal. */
    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private QueryBuilder buildQuery(FindUserData findUserData, String... selectCols) {
        QueryBuilder builder;
        switch (findUserData.domain()) {
            case "user" -> builder = QueryBuilder.select(selectCols)
                    .from("users u")
                    .whereActive("u");

            case "role" -> builder = QueryBuilder.select(selectCols)
                    .from("roles r")
                    .join("user_roles ur ON ur.role_id = r.role_id")
                    .join("users u ON u.user_id = ur.user_id")
                    .whereActive("r").whereActive("u");

            case "permission" -> builder = QueryBuilder.select(selectCols)
                    .from("permissions p")
                    .join("role_permissions rp ON rp.permission_id = p.permission_id")
                    .join("roles r ON r.role_id = rp.role_id")
                    .join("user_roles ur ON ur.role_id = r.role_id")
                    .join("users u ON u.user_id = ur.user_id")
                    .whereActive("p").whereActive("u");

            case "appointment" -> builder = QueryBuilder.select(selectCols)
                    .from("appointments a")
                    .join("users u ON u.user_id = a.patient_id")
                    .whereActive("a").whereActive("u");

            case "doctor" -> builder = QueryBuilder.select(selectCols)
                    .from("doctors d")
                    .join("doctor_department dd ON dd.doctor_id = d.doctor_id")
                    .join("departments dep ON dep.department_id = dd.department_id")
                    .join("users u ON u.user_id = d.user_id")
                    .whereActive("d").whereActive("u").whereActive("dep");

            default -> throw new IllegalStateException("Unknown domain: " + findUserData.domain());
        }

        if (!findUserData.userId().isBlank()) {
            builder.and("u.user_id = '" + findUserData.userId() + "'");
        }
        if (!findUserData.username().isBlank()) {
            builder.and("u.username = '" + findUserData.username() + "'");
        }
        return builder;
    }
}
