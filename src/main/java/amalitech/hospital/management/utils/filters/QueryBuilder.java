package amalitech.hospital.management.utils.filters;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent, single-use SQL SELECT query builder.
 * Not thread-safe — build one instance per query.
 */
public class QueryBuilder {

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

    public static QueryBuilder select(String... columns) {
        List<String> cols = new ArrayList<>();
        for (String c : columns) cols.add(c);
        return new QueryBuilder(cols);
    }

    public static QueryBuilder selectAll() {
        List<String> cols = new ArrayList<>();
        cols.add("*");
        return new QueryBuilder(cols);
    }

    public QueryBuilder distinct() {
        this.distinct = true;
        return this;
    }

    public QueryBuilder from(String tableExpr) {
        this.fromExpr = tableExpr;
        return this;
    }

    public QueryBuilder join(String joinExpr) {
        joins.add("INNER JOIN " + joinExpr);
        return this;
    }

    public QueryBuilder leftJoin(String joinExpr) {
        joins.add("LEFT JOIN " + joinExpr);
        return this;
    }

    public QueryBuilder where(String condition) {
        conditions.add(condition);
        return this;
    }

    public QueryBuilder and(String condition) {
        conditions.add(condition);
        return this;
    }

    public QueryBuilder whereActive() {
        conditions.add("deleted_at IS NULL");
        return this;
    }

    public QueryBuilder whereActive(String alias) {
        conditions.add(alias + ".deleted_at IS NULL");
        return this;
    }

    public QueryBuilder whereLike(String column, String paramPlaceholder) {
        conditions.add(column + " ILIKE " + paramPlaceholder);
        return this;
    }

    public QueryBuilder whereSearchAny(String paramPlaceholder, String... columns) {
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

    public QueryBuilder groupBy(String... columns) {
        for (String c : columns) groupByParts.add(c);
        return this;
    }

    public QueryBuilder having(String condition) {
        this.havingClause = condition;
        return this;
    }

    public QueryBuilder orderBy(String column) {
        orderParts.add(column + " ASC");
        return this;
    }

    public QueryBuilder orderBy(String column, SortDir dir) {
        orderParts.add(column + " " + dir.name());
        return this;
    }

    public QueryBuilder limit(int n) {
        this.limitVal = n;
        return this;
    }

    public QueryBuilder offset(int n) {
        this.offsetVal = n;
        return this;
    }

    public String build() {
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

    @Override
    public String toString() {
        return build();
    }
}
