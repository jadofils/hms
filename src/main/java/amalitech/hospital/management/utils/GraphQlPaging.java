package amalitech.hospital.management.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} for a GraphQL resolver from the same {@code "property,direction"}
 * sort format REST's own {@code ?sort=} query param already uses. Spring binds a REST
 * controller's {@code Pageable} parameter (and its {@code sort}) automatically from the
 * query string; a GraphQL resolver instead receives a plain {@code @Argument String sort}
 * with no equivalent built-in binding — every listing resolver parses it through this one
 * shared helper instead of duplicating the split/parse logic 13 times.
 *
 * <p>A {@code null}/blank {@code sort} means unsorted — the exact same default
 * {@code PageRequest.of(page, size)} (no {@link Sort}) a REST caller gets by omitting
 * {@code ?sort=} entirely. Before this, every GraphQL listing query hardcoded
 * {@code PageRequest.of(page, size)} with no way to request an order at all — the
 * underlying service methods (e.g. {@code PatientService.getPatients}) already read
 * {@code Pageable.getSort()} to drive `@FindUserData`'s whitelisted `ORDER BY`, or,
 * for the plain-JPA listings, get it applied automatically by Hibernate — so wiring
 * {@code sort} through here needed no service-layer change at all, only this parsing
 * step on the GraphQL side.
 */
public final class GraphQlPaging {

    private GraphQlPaging() {}

    public static Pageable of(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size);
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
