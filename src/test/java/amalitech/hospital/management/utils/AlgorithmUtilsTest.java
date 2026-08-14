package amalitech.hospital.management.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlgorithmUtilsTest {

    // ── mergeSort ────────────────────────────────────────────────────────────

    @Test
    void mergeSort_sortsAscendingByComparator() {
        List<Integer> list = new ArrayList<>(List.of(5, 3, 8, 1, 9, 2));
        AlgorithmUtils.mergeSort(list, Comparator.naturalOrder());
        assertThat(list).containsExactly(1, 2, 3, 5, 8, 9);
    }

    @Test
    void mergeSort_sortsDescendingWithReversedComparator() {
        List<Integer> list = new ArrayList<>(List.of(5, 3, 8, 1, 9, 2));
        AlgorithmUtils.mergeSort(list, Comparator.<Integer>naturalOrder().reversed());
        assertThat(list).containsExactly(9, 8, 5, 3, 2, 1);
    }

    @Test
    void mergeSort_noopOnNullList() {
        List<Integer> nullList = null;
        AlgorithmUtils.mergeSort(nullList, Comparator.naturalOrder());
        // no exception is the assertion
    }

    @Test
    void mergeSort_noopOnEmptyOrSingleElementList() {
        List<Integer> empty = new ArrayList<>();
        AlgorithmUtils.mergeSort(empty, Comparator.naturalOrder());
        assertThat(empty).isEmpty();

        List<Integer> single = new ArrayList<>(List.of(42));
        AlgorithmUtils.mergeSort(single, Comparator.naturalOrder());
        assertThat(single).containsExactly(42);
    }

    @Test
    void mergeSort_preservesDuplicates() {
        List<Integer> list = new ArrayList<>(List.of(3, 1, 3, 2, 1));
        AlgorithmUtils.mergeSort(list, Comparator.naturalOrder());
        assertThat(list).containsExactly(1, 1, 2, 3, 3);
    }

    // ── binarySearch ─────────────────────────────────────────────────────────

    @Test
    void binarySearch_findsElementByExtractedKey() {
        List<String> list = List.of("aa", "bbb", "cccc", "ddddd");
        int index = AlgorithmUtils.binarySearch(list, 4, String::length);
        assertThat(index).isEqualTo(2);
    }

    @Test
    void binarySearch_returnsMinusOne_whenKeyNotPresent() {
        List<String> list = List.of("aa", "bbb", "cccc", "ddddd");
        assertThat(AlgorithmUtils.binarySearch(list, 99, String::length)).isEqualTo(-1);
    }

    @Test
    void binarySearch_returnsMinusOne_whenListNull() {
        assertThat(AlgorithmUtils.binarySearch(null, 1, String::length)).isEqualTo(-1);
    }

    @Test
    void binarySearch_returnsMinusOne_whenListEmpty() {
        assertThat(AlgorithmUtils.binarySearch(List.of(), 1, String::length)).isEqualTo(-1);
    }

    @Test
    void binarySearch_returnsMinusOne_whenTargetKeyNull() {
        List<String> list = List.of("aa", "bbb");
        assertThat(AlgorithmUtils.binarySearch(list, null, String::length)).isEqualTo(-1);
    }

    @Test
    void binarySearch_findsFirstAndLastElement() {
        List<Integer> list = List.of(1, 2, 3, 4, 5);
        assertThat(AlgorithmUtils.binarySearch(list, 1, i -> i)).isEqualTo(0);
        assertThat(AlgorithmUtils.binarySearch(list, 5, i -> i)).isEqualTo(4);
    }
}
