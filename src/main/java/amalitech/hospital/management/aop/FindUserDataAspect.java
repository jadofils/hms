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
    public Object executeFindUserData(ProceedingJoinPoint pjp, FindUserData findUserData) {
        Object[] args = pjp.getArgs();
        boolean paginated = args.length >= 2 && args[0] instanceof Integer && args[1] instanceof Integer;

        if (!paginated) {
            // A single String arg on a non-paginated call is a runtime filter value —
            // same "read it off the method's own arguments, not the annotation" trick as
            // page/size/sortBy/sortDir above, needed here because an annotation attribute
            // can only ever be a compile-time constant, never a per-request value like
            // the email a caller just typed into a forgot-password form. Currently only
            // meaningful for domain="user" (see AuthService.findUserByEmail) — every
            // other non-paginated caller passes no args at all.
            String filter1 = args.length == 1 && args[0] instanceof String s && !s.isBlank() ? s : null;
            QueryBuilder builder = buildQuery(findUserData, filter1, null, selectColumnsFor(findUserData.domain()));
            return entityManager.createNativeQuery(builder.build()).getResultList();
        }

        int page = (Integer) args[0];
        int size = (Integer) args[1];
        String sortBy = args.length > 2 && args[2] instanceof String s ? s : null;
        String sortDir = args.length > 3 && args[3] instanceof String s ? s : null;

        // Optional trailing filter args (currently only meaningful for domain="patient":
        // status/gender). Like sortBy/sortDir above, these are read off the method's own
        // runtime arguments rather than the annotation. Callers must validate these
        // against their domain's own enum's allowed values *before* calling here (see
        // PatientService.getPatients) — buildQuery concatenates them directly, the same
        // way findUserData.userId()/username() already do.
        String filter1 = args.length > 4 && args[4] instanceof String s && !s.isBlank() ? s : null;
        String filter2 = args.length > 5 && args[5] instanceof String s && !s.isBlank() ? s : null;

        // A frontend-chosen sort column is only ever resolved against this domain's own
        // SELECT list (never concatenated into the query raw) — anything unrecognized
        // falls back to the first selected column, so paginated results stay
        // deterministic page-to-page either way (Postgres gives no ordering guarantee
        // at all without an ORDER BY, sort request or not).
        String orderColumn = resolveSortColumn(findUserData.domain(), sortBy);
        QueryBuilder.SortDir direction = "DESC".equalsIgnoreCase(sortDir) ? QueryBuilder.SortDir.DESC : QueryBuilder.SortDir.ASC;

        QueryBuilder rowsBuilder = buildQuery(findUserData, filter1, filter2, selectColumnsFor(findUserData.domain()))
                .orderBy(orderColumn, direction)
                .limit(size)
                .offset(page * size);
        QueryBuilder countBuilder = buildQuery(findUserData, filter1, filter2, countExpressionFor(findUserData.domain()));

        List<?> rows = entityManager.createNativeQuery(rowsBuilder.build()).getResultList();
        Number total = (Number) entityManager.createNativeQuery(countBuilder.build()).getSingleResult();
        return new PagedRawResult(rows, total.longValue());
    }

    private String[] selectColumnsFor(String domain) {
        return switch (domain) {
            case "user" -> new String[]{"u.user_id", "u.username", "u.email", "u.is_active"};
            case "role" -> new String[]{"r.role_id", "r.role_name"};
            case "permission" -> new String[]{"p.permission_id", "p.resource", "p.action"};
            // Appointment->Patient and Appointment->Doctor are both plain many-to-one FKs
            // (unlike Doctor<->Department's many-to-many above), so joining both here
            // can't fan an appointment out into duplicate rows — safe to include names.
            case "appointment" -> new String[]{
                    "a.appointment_id", "a.patient_id", "a.doctor_id",
                    "p.first_name AS patient_first_name", "p.last_name AS patient_last_name",
                    "d.first_name AS doctor_first_name", "d.last_name AS doctor_last_name",
                    "a.appointment_date", "a.status", "a.reason"
            };
            // Doctor<->Department is many-to-many, so joining departments in here would
            // fan a doctor with N departments out into N duplicate rows (breaking both
            // pagination and the COUNT(*) used for it). Department membership is exposed
            // instead via the single-item DoctorResponse (through the JPA entity's own
            // @ManyToMany collection) — this listing only needs the doctor's own columns.
            case "doctor" -> new String[]{
                    "d.doctor_id", "d.first_name", "d.last_name", "d.specialization", "d.phone", "d.email"
            };
            case "patient" -> new String[]{
                    "p.patient_id", "p.first_name", "p.last_name", "p.dob",
                    "p.gender", "p.phone", "p.email", "p.address", "p.status"
            };
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
            // A single literal space each side (not \s+ repeated) — every selectColumnsFor
            // entry already uses exactly one space around "AS" consistently, and a fixed,
            // non-repeated separator here avoids the superlinear backtracking risk a
            // quantified \s+ on both sides of a case-insensitive alternation can have.
            String[] parts = column.split("(?i) as ", 2);
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

    private QueryBuilder buildQuery(FindUserData findUserData, String filter1, String filter2, String... selectCols) {
        QueryBuilder builder;
        switch (findUserData.domain()) {
            case "user" -> {
                builder = QueryBuilder.select(selectCols)
                        .from("users u")
                        .whereActive("u");
                // filter1 = an email to check existence for (AuthService.findUserByEmail,
                // the forgot-password flow's first step) — raw user input from a public,
                // unauthenticated endpoint, unlike every other concatenated value in this
                // method, so it's escaped rather than trusted the way userId()/username()
                // currently are (see this class's own Javadoc on that existing risk).
                if (filter1 != null) builder.and("u.email = '" + escapeSqlLiteral(filter1) + "'");
            }

            // .distinct() — a role/permission held by more than one active user would
            // otherwise repeat once per holder (this join fans out per user_roles/
            // role_permissions row, and the select list carries no user-identifying
            // column to make each repeat distinguishable); see countExpressionFor's
            // matching COUNT(DISTINCT ...) for the paginated total this must stay
            // consistent with.
            case "role" -> builder = QueryBuilder.select(selectCols)
                    .distinct()
                    .from("roles r")
                    .join("user_roles ur ON ur.role_id = r.role_id")
                    .join("users u ON u.user_id = ur.user_id")
                    .whereActive("r").whereActive("u");

            case "permission" -> builder = QueryBuilder.select(selectCols)
                    .distinct()
                    .from("permissions p")
                    .join("role_permissions rp ON rp.permission_id = p.permission_id")
                    .join("roles r ON r.role_id = rp.role_id")
                    .join("user_roles ur ON ur.role_id = r.role_id")
                    .join("users u ON u.user_id = ur.user_id")
                    .whereActive("p").whereActive("u");

            case "appointment" -> {
                builder = QueryBuilder.select(selectCols)
                        .from("appointments a")
                        .join("patients p ON p.patient_id = a.patient_id")
                        .join("doctors d ON d.doctor_id = a.doctor_id")
                        .whereActive("a").whereActive("p").whereActive("d");
                // filter1 = status — already validated against AppointmentStatus's own
                // dbValues by AppointmentService before this call.
                if (filter1 != null) builder.and("a.status = '" + filter1 + "'");
            }

            case "doctor" -> builder = QueryBuilder.select(selectCols)
                    .from("doctors d")
                    .whereActive("d");

            case "patient" -> {
                builder = QueryBuilder.select(selectCols)
                        .from("patients p")
                        .whereActive("p");
                // filter1 = status, filter2 = gender — both already validated against
                // PatientStatus/Gender's own dbValues by PatientService before this call.
                if (filter1 != null) builder.and("p.status = '" + filter1 + "'");
                if (filter2 != null) builder.and("p.gender = '" + filter2 + "'");
            }

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

    /** The "role"/"permission" cases below join back through {@code user_roles}/
     *  {@code users} — a role or permission held by N active users would otherwise
     *  fan out into N duplicate rows (see {@link #buildQuery}'s {@code .distinct()} on
     *  those two cases), so the accompanying count must dedupe on the same primary key
     *  rather than counting every join row via a plain {@code COUNT(*)}. */
    private String countExpressionFor(String domain) {
        return switch (domain) {
            case "role" -> "COUNT(DISTINCT r.role_id)";
            case "permission" -> "COUNT(DISTINCT p.permission_id)";
            default -> "COUNT(*)";
        };
    }

    /** Doubles every single quote — the standard SQL string-literal escape — so a value
     *  containing one (a single quote is a legal character in an email's local part, and
     *  the {@code @Email} validator that runs before this doesn't reject it) can't break
     *  out of the quoted literal it's concatenated into. */
    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
