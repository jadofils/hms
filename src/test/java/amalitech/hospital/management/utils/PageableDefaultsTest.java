package amalitech.hospital.management.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageableDefaultsTest {

    @Test
    void withDefaultSort_returnsTheSamePageable_whenAlreadySorted() {
        Pageable sorted = PageRequest.of(1, 10, Sort.by("username").descending());

        Pageable result = PageableDefaults.withDefaultSort(sorted, "roleName", Sort.Direction.ASC);

        assertThat(result).isSameAs(sorted);
    }

    @Test
    void withDefaultSort_appliesTheGivenDefault_whenUnsorted() {
        Pageable unsorted = PageRequest.of(2, 15);

        Pageable result = PageableDefaults.withDefaultSort(unsorted, "roleName", Sort.Direction.ASC);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(15);
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "roleName"));
    }

    @Test
    void withDefaultSort_honorsTheRequestedDirection() {
        Pageable unsorted = PageRequest.of(0, 20);

        Pageable result = PageableDefaults.withDefaultSort(unsorted, "createdAt", Sort.Direction.DESC);

        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
