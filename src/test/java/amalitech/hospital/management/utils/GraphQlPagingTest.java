package amalitech.hospital.management.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of {@link GraphQlPaging}'s parsing logic, independent of any one
 * resolver — the same "test the util itself" convention {@code AlgorithmUtilsTest}/
 * {@code QueryBuilderTest} already follow.
 */
class GraphQlPagingTest {

    @Test
    void of_returnsUnsortedPageable_whenSortIsNull() {
        Pageable pageable = GraphQlPaging.of(1, 20, null);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void of_returnsUnsortedPageable_whenSortIsBlank() {
        Pageable pageable = GraphQlPaging.of(0, 20, "   ");

        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void of_parsesPropertyAndAscendingDirection_whenDirectionOmitted() {
        Pageable pageable = GraphQlPaging.of(0, 20, "lastName");

        Sort.Order order = pageable.getSort().getOrderFor("lastName");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void of_parsesDescendingDirection_caseInsensitively() {
        Pageable pageable = GraphQlPaging.of(0, 20, "lastName,DESC");

        Sort.Order order = pageable.getSort().getOrderFor("lastName");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void of_treatsAnyNonDescValueAsAscending() {
        Pageable pageable = GraphQlPaging.of(0, 20, "lastName,bogus");

        Sort.Order order = pageable.getSort().getOrderFor("lastName");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void of_trimsWhitespaceAroundPropertyAndDirection() {
        Pageable pageable = GraphQlPaging.of(0, 20, " lastName , desc ");

        Sort.Order order = pageable.getSort().getOrderFor("lastName");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
