package amalitech.hospital.management.utils.filters;

import java.util.List;

/**
 * Raw native-query pagination result: the requested page of rows plus the total row
 * count (ignoring LIMIT/OFFSET) needed to build a Spring Data {@code Page}.
 */
public record PagedRawResult(List<?> rows, long total) {}
