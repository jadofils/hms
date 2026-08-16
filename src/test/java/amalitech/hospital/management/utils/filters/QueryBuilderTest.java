package amalitech.hospital.management.utils.filters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests, no Spring context needed — {@link QueryBuilder} is a plain fluent
 * string-building utility.
 */
class QueryBuilderTest {

    @Test
    void select_buildsBasicQuery() {
        String sql = QueryBuilder.select("id", "name").from("users").build();
        assertThat(sql).isEqualTo("SELECT id, name FROM users");
    }

    @Test
    void selectAll_usesStarColumn() {
        String sql = QueryBuilder.selectAll().from("users").build();
        assertThat(sql).isEqualTo("SELECT * FROM users");
    }

    @Test
    void distinct_addsDistinctKeyword() {
        String sql = QueryBuilder.select("id").distinct().from("users").build();
        assertThat(sql).isEqualTo("SELECT DISTINCT id FROM users");
    }

    @Test
    void build_throwsIllegalState_whenFromNeverSet() {
        assertThatThrownBy(() -> QueryBuilder.select("id").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FROM clause is required");
    }

    @Test
    void build_throwsIllegalState_whenFromBlank() {
        assertThatThrownBy(() -> QueryBuilder.select("id").from("   ").build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void join_addsInnerJoin() {
        String sql = QueryBuilder.select("u.id").from("users u")
                .join("roles r ON r.id = u.role_id")
                .build();
        assertThat(sql).isEqualTo("SELECT u.id FROM users u INNER JOIN roles r ON r.id = u.role_id");
    }

    @Test
    void leftJoin_addsLeftJoin() {
        String sql = QueryBuilder.select("u.id").from("users u")
                .leftJoin("roles r ON r.id = u.role_id")
                .build();
        assertThat(sql).isEqualTo("SELECT u.id FROM users u LEFT JOIN roles r ON r.id = u.role_id");
    }

    @Test
    void multipleJoins_areAppendedInOrder() {
        String sql = QueryBuilder.select("u.id").from("users u")
                .join("a ON 1=1")
                .leftJoin("b ON 1=1")
                .build();
        assertThat(sql).isEqualTo("SELECT u.id FROM users u INNER JOIN a ON 1=1 LEFT JOIN b ON 1=1");
    }

    @Test
    void where_and_combineWithAnd() {
        String sql = QueryBuilder.select("id").from("users")
                .where("is_active = true")
                .and("deleted_at IS NULL")
                .build();
        assertThat(sql).isEqualTo("SELECT id FROM users WHERE is_active = true AND deleted_at IS NULL");
    }

    @Test
    void whereActive_noAlias_addsBareCondition() {
        String sql = QueryBuilder.select("id").from("users").whereActive().build();
        assertThat(sql).isEqualTo("SELECT id FROM users WHERE deleted_at IS NULL");
    }

    @Test
    void whereActive_withAlias_qualifiesColumn() {
        String sql = QueryBuilder.select("u.id").from("users u").whereActive("u").build();
        assertThat(sql).isEqualTo("SELECT u.id FROM users u WHERE u.deleted_at IS NULL");
    }

    @Test
    void whereLike_addsIlikeCondition() {
        String sql = QueryBuilder.select("id").from("users").whereLike("username", ":search").build();
        assertThat(sql).isEqualTo("SELECT id FROM users WHERE username ILIKE :search");
    }

    @Test
    void whereSearchAny_combinesColumnsWithOr() {
        String sql = QueryBuilder.select("id").from("users")
                .whereSearchAny(":search", "username", "email")
                .build();
        assertThat(sql).isEqualTo("SELECT id FROM users WHERE (username ILIKE :search OR email ILIKE :search)");
    }

    @Test
    void whereSearchAny_noColumns_addsNoCondition() {
        String sql = QueryBuilder.select("id").from("users").whereSearchAny(":search").build();
        assertThat(sql).isEqualTo("SELECT id FROM users");
    }

    @Test
    void groupBy_addsGroupByClause() {
        String sql = QueryBuilder.select("dept", "COUNT(*)").from("doctors")
                .groupBy("dept")
                .build();
        assertThat(sql).isEqualTo("SELECT dept, COUNT(*) FROM doctors GROUP BY dept");
    }

    @Test
    void having_addsHavingClause_onlyAfterGroupBy() {
        String sql = QueryBuilder.select("dept", "COUNT(*)").from("doctors")
                .groupBy("dept")
                .having("COUNT(*) > 1")
                .build();
        assertThat(sql).isEqualTo("SELECT dept, COUNT(*) FROM doctors GROUP BY dept HAVING COUNT(*) > 1");
    }

    @Test
    void having_blank_isIgnored() {
        String sql = QueryBuilder.select("dept").from("doctors")
                .groupBy("dept")
                .having("   ")
                .build();
        assertThat(sql).doesNotContain("HAVING");
    }

    @Test
    void orderBy_defaultsToAscending() {
        String sql = QueryBuilder.select("id").from("users").orderBy("username").build();
        assertThat(sql).isEqualTo("SELECT id FROM users ORDER BY username ASC");
    }

    @Test
    void orderBy_withExplicitDescDirection() {
        String sql = QueryBuilder.select("id").from("users").orderBy("username", QueryBuilder.SortDir.DESC).build();
        assertThat(sql).isEqualTo("SELECT id FROM users ORDER BY username DESC");
    }

    @Test
    void orderBy_withExplicitAscDirection() {
        String sql = QueryBuilder.select("id").from("users").orderBy("username", QueryBuilder.SortDir.ASC).build();
        assertThat(sql).isEqualTo("SELECT id FROM users ORDER BY username ASC");
    }

    @Test
    void limitAndOffset_appendPaginationClauses() {
        String sql = QueryBuilder.select("id").from("users").limit(20).offset(40).build();
        assertThat(sql).isEqualTo("SELECT id FROM users LIMIT 20 OFFSET 40");
    }

    @Test
    void limitAndOffset_omittedWhenNegative() {
        String sql = QueryBuilder.select("id").from("users").build();
        assertThat(sql).doesNotContain("LIMIT").doesNotContain("OFFSET");
    }

    @Test
    void toString_delegatesToBuild() {
        QueryBuilder builder = QueryBuilder.select("id").from("users");
        assertThat(builder.toString()).isEqualTo(builder.build());
    }

    @Test
    void fullQuery_combinesEveryClauseInOrder() {
        String sql = QueryBuilder.select("d.dept", "COUNT(d.id) AS cnt")
                .distinct()
                .from("doctors d")
                .join("departments dep ON dep.id = d.dept_id")
                .whereActive("d")
                .and("dep.active = true")
                .groupBy("d.dept")
                .having("COUNT(d.id) > 0")
                .orderBy("cnt", QueryBuilder.SortDir.DESC)
                .limit(10)
                .offset(0)
                .build();

        assertThat(sql).isEqualTo(
                "SELECT DISTINCT d.dept, COUNT(d.id) AS cnt FROM doctors d "
                        + "INNER JOIN departments dep ON dep.id = d.dept_id "
                        + "WHERE d.deleted_at IS NULL AND dep.active = true "
                        + "GROUP BY d.dept HAVING COUNT(d.id) > 0 "
                        + "ORDER BY cnt DESC LIMIT 10 OFFSET 0");
    }
}
