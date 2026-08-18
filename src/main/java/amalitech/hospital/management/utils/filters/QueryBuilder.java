package amalitech.hospital.management.utils.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent, single-use SQL SELECT query builder.
 * Not thread-safe — build one instance per query.
 */
public class QueryBuilder {

    private static final Logger log = LoggerFactory.getLogger(QueryBuilder.class);

    public enum SortDir { ASC, DESC }

    private final List<String> selectCols;
    private boolean distinct = false;
    private String fromExpr;
    private final List<String> joins        = new ArrayList<>();
    private final List<String> conditions   = new ArrayList<>();
    private final List<String> groupByParts = new ArrayList<>();
    private String havingClause;
    private final List<String> orderParts   = new ArrayList<>();
    private int limitVal  = -1;
    private int offsetVal = -1;

    private QueryBuilder(List<String> columns) {
        this.selectCols = columns;
    }

    // Fires to start every native query FindUserDataAspect/SqlQueryBuilderAspect build —
    // e.g. FindUserDataAspect.buildQuery's "user" case, SqlQueryBuilderAspect's
    // "findRolesWithPermissionCount" case.
    public static QueryBuilder select(String... columns) {
        log.debug("QueryBuilder.select invoked — called by FindUserDataAspect.buildQuery/SqlQueryBuilderAspect.executeSqlQuery");
        List<String> cols = new ArrayList<>();
        for (String c : columns) cols.add(c);

        return new QueryBuilder(cols);
    }

    // Not currently called by any real caller — a select("*")-equivalent alternative
    // entry point alongside select() above.
    public static QueryBuilder selectAll() {
        log.debug("QueryBuilder.selectAll invoked — no current real caller; alternative to select() for a bare SELECT *");
        List<String> cols = new ArrayList<>();
        cols.add("*");
        return new QueryBuilder(cols);
    }

    // Fires when FindUserDataAspect.buildQuery's "role"/"permission" cases dedupe rows
    // fanned out by their user_roles/role_permissions joins.
    public QueryBuilder distinct() {
        log.debug("QueryBuilder.distinct invoked — called by FindUserDataAspect.buildQuery (role/permission domains)");
        this.distinct = true;
        return this;
    }

    // Fires for every native query built, called by FindUserDataAspect.buildQuery and
    // SqlQueryBuilderAspect.executeSqlQuery to set the FROM table.
    public QueryBuilder from(String tableExpr) {
        log.debug("QueryBuilder.from invoked — called by FindUserDataAspect.buildQuery/SqlQueryBuilderAspect.executeSqlQuery");
        this.fromExpr = tableExpr;
        return this;
    }

    // Fires for every joined domain query — e.g. FindUserDataAspect.buildQuery's
    // "appointment"/"role"/"permission" cases, SqlQueryBuilderAspect's
    // "findDoctorsByDepartment"/"findDepartmentsWithDoctors" cases.
    public QueryBuilder join(String joinExpr) {
        log.debug("QueryBuilder.join invoked — called by FindUserDataAspect.buildQuery/SqlQueryBuilderAspect.executeSqlQuery");
        joins.add("INNER JOIN " + joinExpr);

        return this;
    }

    // Fires only for SqlQueryBuilderAspect's "findRolesWithPermissionCount" case, to
    // keep a role with zero permissions in the result instead of excluding it.
    public QueryBuilder leftJoin(String joinExpr) {
        log.debug("QueryBuilder.leftJoin invoked — called by SqlQueryBuilderAspect.executeSqlQuery (findRolesWithPermissionCount)");
        joins.add("LEFT JOIN " + joinExpr);

        return this;
    }

    // Not currently called by any real caller — every existing caller uses and()
    // instead for its first condition too.
    public QueryBuilder where(String condition) {
        log.debug("QueryBuilder.where invoked — no current real caller; every caller uses and() instead");
        conditions.add(condition);
        return this;
    }

    // Fires for every filter/userId/username condition FindUserDataAspect.buildQuery
    // appends while assembling a domain's WHERE clause.
    public QueryBuilder and(String condition) {
        log.debug("QueryBuilder.and invoked — called by FindUserDataAspect.buildQuery");
        conditions.add(condition);
        return this;
    }

    // Not currently called by any real caller — every existing caller uses the
    // aliased whereActive(String) overload below instead.
    public QueryBuilder whereActive() {
        log.debug("QueryBuilder.whereActive() invoked — no current real caller; callers use the aliased overload instead");
        conditions.add("deleted_at IS NULL");
        return this;
    }

    // Fires for every domain query, called by FindUserDataAspect.buildQuery and
    // SqlQueryBuilderAspect.executeSqlQuery to exclude soft-deleted rows.
    public QueryBuilder whereActive(String alias) {
        log.debug("QueryBuilder.whereActive(alias) invoked — called by FindUserDataAspect.buildQuery/SqlQueryBuilderAspect.executeSqlQuery");
        conditions.add(alias + ".deleted_at IS NULL");
        return this;
    }

    // Not currently called by any real caller — a single-column ILIKE helper alongside
    // whereSearchAny() below.
    public QueryBuilder whereLike(String column, String paramPlaceholder) {
        log.debug("QueryBuilder.whereLike invoked — no current real caller");
        conditions.add(column + " ILIKE " + paramPlaceholder);
        return this;
    }

    // Not currently called by any real caller — a multi-column ILIKE-any helper for a
    // future free-text search filter.
    public QueryBuilder whereSearchAny(String paramPlaceholder, String... columns) {
        log.debug("QueryBuilder.whereSearchAny invoked — no current real caller");
        if (columns.length == 0) return this;
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(columns[i]).append(" ILIKE ").append(paramPlaceholder);
        }
        sb.append(")");
        conditions.add(sb.toString());
        return this;
    }

    // Fires for SqlQueryBuilderAspect's "findDepartmentsWithDoctors"/
    // "findRolesWithPermissionCount" GROUP BY cases.
    public QueryBuilder groupBy(String... columns) {
        log.debug("QueryBuilder.groupBy invoked — called by SqlQueryBuilderAspect.executeSqlQuery");
        groupByParts.addAll(Arrays.asList(columns));
        return this;
    }

    // Fires only for SqlQueryBuilderAspect's "findDepartmentsWithDoctors" case, to keep
    // only departments with at least one active doctor.
    public QueryBuilder having(String condition) {
        log.debug("QueryBuilder.having invoked — called by SqlQueryBuilderAspect.executeSqlQuery (findDepartmentsWithDoctors)");
        this.havingClause = condition;
        return this;
    }

    // Not currently called by any real caller — every existing caller uses the
    // explicit-direction orderBy(column, dir) overload below instead.
    public QueryBuilder orderBy(String column) {
        log.debug("QueryBuilder.orderBy(column) invoked — no current real caller; callers use the direction overload instead");
        orderParts.add(column + " ASC");
        return this;
    }

    // Fires for every paginated @FindUserData listing, called by
    // FindUserDataAspect.executeFindUserData to apply its resolved, whitelisted sort column.
    public QueryBuilder orderBy(String column, SortDir dir) {
        log.debug("QueryBuilder.orderBy(column, dir) invoked — called by FindUserDataAspect.executeFindUserData");
        orderParts.add(column + " " + dir.name());
        return this;
    }

    // Fires for every paginated @FindUserData listing, called by
    // FindUserDataAspect.executeFindUserData to page its rows query.
    public QueryBuilder limit(int n) {
        log.debug("QueryBuilder.limit invoked — called by FindUserDataAspect.executeFindUserData");
        this.limitVal = n;
        return this;
    }

    // Fires for every paginated @FindUserData listing, called by
    // FindUserDataAspect.executeFindUserData to page its rows query.
    public QueryBuilder offset(int n) {
        log.debug("QueryBuilder.offset invoked — called by FindUserDataAspect.executeFindUserData");
        this.offsetVal = n;
        return this;
    }

    // Fires once per query, called by FindUserDataAspect.executeFindUserData and
    // SqlQueryBuilderAspect.executeSqlQuery to render the final SQL before running it.
    public String build() {
        log.debug("QueryBuilder.build invoked — called by FindUserDataAspect.executeFindUserData/SqlQueryBuilderAspect.executeSqlQuery");
        if (fromExpr == null || fromExpr.isBlank()) {
            throw new IllegalStateException("FROM clause is required but was not set.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (distinct) sb.append("DISTINCT ");
        sb.append(String.join(", ", selectCols));
        sb.append(" FROM ").append(fromExpr);

        for (String j : joins) sb.append(" ").append(j);

        if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
        if (!groupByParts.isEmpty()) sb.append(" GROUP BY ").append(String.join(", ", groupByParts));
        if (havingClause != null && !havingClause.isBlank()) sb.append(" HAVING ").append(havingClause);
        if (!orderParts.isEmpty()) sb.append(" ORDER BY ").append(String.join(", ", orderParts));
        if (limitVal >= 0) sb.append(" LIMIT ").append(limitVal);
        if (offsetVal >= 0) sb.append(" OFFSET ").append(offsetVal);

        return sb.toString();
    }

    // Not currently called by any real caller — lets a QueryBuilder be logged/printed
    // directly as its rendered SQL, delegating to build() above.
    @Override
    public String toString() {
        log.debug("QueryBuilder.toString invoked — no current real caller; delegates to QueryBuilder.build");
        return build();
    }
}
