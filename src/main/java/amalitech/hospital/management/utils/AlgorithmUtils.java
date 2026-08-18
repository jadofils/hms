package amalitech.hospital.management.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Standalone in-memory sorting and searching algorithms used across the service
 * layer to order cached collections and locate elements without a database round-trip.
 */
public final class AlgorithmUtils {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmUtils.class);

    private AlgorithmUtils() {}

    // ── Merge Sort ────────────────────────────────────────────────────────────
    // Fires for every AlgorithmAspect mergeSort call — e.g. RoleService.sort,
    // AppointmentService.sort — invoked by the aspect's advice, not directly by services.
    public static <T> void mergeSort(List<T> list, Comparator<T> comparator) {
        log.debug("AlgorithmUtils.mergeSort invoked — called by AlgorithmAspect.executeAlgorithm");
        if (list == null || list.size() <= 1) return;
        List<T> temp = new ArrayList<>(list);
        mergeSortHelper(list, temp, 0, list.size() - 1, comparator);
    }

    // Fires recursively for every mergeSort call, called only from mergeSort above and
    // itself.
    private static <T> void mergeSortHelper(List<T> list, List<T> temp,
                                            int left, int right,
                                            Comparator<T> comparator) {
        log.debug("AlgorithmUtils.mergeSortHelper invoked — called by AlgorithmUtils.mergeSort");
        if (left >= right) return;
        int mid = left + (right - left) / 2;
        mergeSortHelper(list, temp, left, mid, comparator);
        mergeSortHelper(list, temp, mid + 1, right, comparator);
        merge(list, temp, left, mid, right, comparator);
    }

    // Fires once per mergeSortHelper split, called only from mergeSortHelper above to
    // merge its two sorted halves.
    private static <T> void merge(List<T> list, List<T> temp,
                                  int left, int mid, int right,
                                  Comparator<T> comparator) {
        log.debug("AlgorithmUtils.merge invoked — called by AlgorithmUtils.mergeSortHelper");
        for (int k = left; k <= right; k++) temp.set(k, list.get(k));
        int i = left, j = mid + 1, k = left;
        while (i <= mid && j <= right) {
            if (comparator.compare(temp.get(i), temp.get(j)) <= 0) {
                list.set(k++, temp.get(i++));
            } else {
                list.set(k++, temp.get(j++));
            }
        }
        while (i <= mid) list.set(k++, temp.get(i++));
    }

    // ── Binary Search ─────────────────────────────────────────────────────────
    /**
     * Key type is deliberately not bound to {@code Comparable<K>} in the signature —
     * the only caller (AlgorithmAspect) receives its arguments as reflected
     * {@code Object[]} from a join point, so it never has a real bounded generic
     * type to pass in, only an erased {@code Object} key and a raw {@code Function}.
     * The cast below is unchecked; it throws {@link ClassCastException} at runtime
     * if keyExtractor produces a value that isn't actually comparable to targetKey.
     */
    // Fires for every AlgorithmAspect binarySearch call — e.g.
    // AppointmentService.search (via throwIfDoctorDoubleBooked) — invoked by the
    // aspect's advice, not directly by services.
    @SuppressWarnings("unchecked")
    public static <T> int binarySearch(List<T> list, Object targetKey, Function<T, ?> keyExtractor) {
        log.debug("AlgorithmUtils.binarySearch invoked — called by AlgorithmAspect.executeAlgorithm");
        if (list == null || list.isEmpty() || targetKey == null) return -1;
        int lo = 0, hi = list.size() - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            Comparable<Object> midKey = (Comparable<Object>) keyExtractor.apply(list.get(mid));
            int cmp = midKey.compareTo(targetKey);
            if (cmp == 0) return mid;
            else if (cmp < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }
}
