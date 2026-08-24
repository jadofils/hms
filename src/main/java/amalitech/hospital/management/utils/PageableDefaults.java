package amalitech.hospital.management.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Gives a plain {@code repository.findAll(pageable)}/derived-query listing a real,
 * deterministic default order when the caller sends no {@code ?sort=} at all (REST) or
 * no {@code sort} argument (GraphQL — see {@link GraphQlPaging#of}, which returns the
 * exact same unsorted {@code PageRequest} in that case).
 *
 * <p>Without this, an unsorted {@link Pageable} reaches Hibernate as no {@code ORDER BY}
 * clause whatsoever — Postgres then returns rows in whatever order it finds convenient,
 * which is <em>not</em> guaranteed stable across calls or pages, silently breaking
 * pagination correctness (the same row can appear on two different pages, or never
 * appear at all, across two calls). This is a real, different problem from
 * {@code @FindUserData}'s own {@code sortBy}-falls-back-to-first-column logic
 * ({@code FindUserDataAspect.resolveSortColumn}) — that one already covers every
 * {@code @FindUserData}-backed listing (Users, Patients, Doctors, Appointments,
 * assigned Roles, granted Permissions); this utility is for the plain-JPA listings that
 * mechanism doesn't touch at all.
 */
public final class PageableDefaults {

    private static final Logger log = LoggerFactory.getLogger(PageableDefaults.class);

    private PageableDefaults() {}

    /**
     * Returns {@code pageable} unchanged if it already carries a real sort order;
     * otherwise returns an equivalent {@link Pageable} sorted by {@code property} in
     * {@code direction} — the exact column a real caller would use most often for this
     * listing, matching that endpoint's own Swagger/GraphQL {@code sort} example.
     */
    // Fires once at the top of every plain-JPA listing service method — e.g.
    // RoleService.getRoles, InvoiceService.getInvoices — before any repository call.
    public static Pageable withDefaultSort(Pageable pageable, String property, Sort.Direction direction) {
        log.debug("PageableDefaults.withDefaultSort invoked — called by a plain-JPA listing service method (e.g. RoleService.getRoles)");
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(direction, property));
    }
}
