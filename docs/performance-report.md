# Performance Report

The README's Technical Requirements table names two performance-relevant deliverables:
"DSA Integration: Sorting, searching, pagination algorithms" and, under Story 2.2,
"Performance documented and analyzed" (the REST vs GraphQL comparison). This doc covers
both — the algorithmic side first, the REST/GraphQL benchmark below it.

## Sorting & Searching Algorithms

Full detail (real callers, exact code, why a hand-rolled implementation instead of
`Collections.sort`/`Arrays.binarySearch`) lives in
[`story-2.2-receptionist-filtering.md`](story-2.2-receptionist-filtering.md#algorithms-used--sort--search-in-detail);
the summary:

| Algorithm | Complexity | Implementation | Real caller(s) |
|---|---|---|---|
| Merge sort | `O(n log n)` time, `O(n)` space, stable | `utils/AlgorithmUtils.mergeSort` via `@ApplyAlgorithm("mergeSort")` | `RoleService.getRolePermissions` (sorts a role's granted permissions by `resource:action`); `AppointmentService.throwIfDoctorDoubleBooked` (sorts a doctor's appointments by date, as the precondition for the binary search below) |
| Binary search | `O(log n)` time, requires a pre-sorted list | `utils/AlgorithmUtils.binarySearch` via `@ApplyAlgorithm("binarySearch")` | `AppointmentService.throwIfDoctorDoubleBooked` — checks whether a requested appointment slot is already taken, `O(log n)` against the sorted list above instead of a linear scan or a second DB round trip |

Both are used for **in-memory** operations on a collection already loaded for another
reason in the same call — the opposite case from the pagination/sorting below, which
pushes `ORDER BY`/`LIMIT`/`OFFSET` down to the database specifically so rows outside the
current page are never loaded into memory at all. `AlgorithmUtilsTest` unit-tests both
algorithms directly, independent of either real caller.

## Pagination, Sorting & Filtering

This project uses **offset (page-number) pagination** — Spring Data's `Pageable`/
`PagedModel`, translated to `LIMIT`/`OFFSET` either by Hibernate (`findAll(pageable)`) or
directly via `QueryBuilder.limit()/offset()` for the `@FindUserData`-driven listings —
**not cursor/keyset pagination**. Every listing endpoint (`Patient`/`Doctor`/
`Appointment`/`User`/`Role`/`Permission`) shares this one mechanism, sortable by any
column in that domain's own whitelist and, for `Patient`/`Appointment`, filterable by an
enum-backed status. The full comparison against cursor pagination — what each buys you,
what it costs, and why offset fits this project's actual access pattern (an
admin/receptionist paging through a bounded, clinic-scale table, not an unbounded
infinite-scroll feed) — is in
[`story-2.2-receptionist-filtering.md`](story-2.2-receptionist-filtering.md#pagination-strategy-offset-page-number-based-not-cursor-based).

## Live metrics — Actuator & `@Timed`

The benchmark below is a snapshot — 30 iterations, one machine, one moment in time,
regenerated only when someone remembers to re-run it. This section is its always-on
counterpart: every REST controller and GraphQL resolver is instrumented with Micrometer's
`@Timed`, continuously collected and queryable live rather than only on demand.

**`@Timed`, not just Spring Boot's free per-endpoint timer.** Spring Boot's web
autoconfiguration already times every controller method for free under
`http.server.requests` the moment `spring-boot-starter-actuator` is on the classpath — no
code needed. That alone doesn't cover GraphQL resolvers (they aren't served through the
same `DispatcherServlet` handler-mapping path that filter instruments), and it doesn't
give a way to directly compare "the REST way of doing X" against "the GraphQL way of
doing X" as two named, side-by-side series. So every `@RestController` carries
```java
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
```
and every GraphQL resolver (`@Controller` under `resolvers/`) carries the equivalent
```java
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
```
Both are class-level, so every method on that class is timed with no per-method
boilerplate — Micrometer's own `TimedAspect` (registered as a bean in
`config/MetricsConfig.java`, the one piece `@Timed` doesn't work at all without) tags each
recorded sample with `class`/`method` automatically, so `hms.rest.requests` and
`hms.graphql.requests` become two directly comparable metric families, each already
broken down by exactly which operation. That's the live, continuously-collected version
of the one-off benchmark below — `/actuator/metrics/hms.rest.requests` and
`/actuator/metrics/hms.graphql.requests` (or the same data via `/actuator/prometheus` for
a Grafana dashboard) answer the same "REST vs GraphQL, which is faster for this
operation?" question this benchmark answers manually, except continuously, under real
traffic, instead of 30 synthetic iterations on a dev machine.

**What's actually exposed via Actuator, and what deliberately isn't.** Only
`health,info,metrics,prometheus` — never the bare `*` some tutorials reach for. This app
handles patient PII and `SecurityConfig` currently permits every request unauthenticated
(see that class's own Javadoc) — actuator endpoints inherit that exact same gap, so
`env`/`beans`/`configprops`/`heapdump`/`threaddump` (anything that could leak a secret, a
full dependency graph, or a heap dump) stay off. The four exposed are read-only
operational signal with nothing sensitive in them:
- `/actuator/health` — aggregate `UP`/`DOWN` plus, in dev (`show-details: always`) or for
  an authorized caller elsewhere (`show-details: when-authorized`), a breakdown per
  dependency. `db`/`redis` sub-checks are Spring Boot's own autoconfigured
  `HealthIndicator`s — no bespoke code needed, they activate automatically because
  `spring-boot-starter-data-jpa`/the Postgres driver and `spring-boot-starter-data-redis`
  are already on the classpath for reasons that have nothing to do with Actuator.
- `/actuator/info` — `build.*` from `spring-boot-maven-plugin`'s `build-info` goal
  (`pom.xml`), `app.*` from this project's own `info.app.*` in `application.yaml`.
- `/actuator/metrics` — every named metric, including the two `@Timed` families above.
- `/actuator/prometheus` — the same data in Prometheus text-exposition format
  (`micrometer-registry-prometheus`), for scraping into an actual Grafana dashboard rather
  than polling `/actuator/metrics/{name}` by hand.

## REST vs GraphQL

The `ReadMe.md` Deliverables table names this "REST vs GraphQL analysis"; `docs/story-2.2-receptionist-filtering.md` deferred it until GraphQL (Epic 4) existed to compare against. Both styles call the *same* service layer against the *same* PostgreSQL database — this isolates REST's HTTP+DTO-mapping overhead from GraphQL's HTTP+query-parsing+field-resolution overhead. It is not an indexing/database-tuning study (see `FindUserDataAspect`/Section 5.7-equivalent work for that).

**Generated:** 2026-08-17T20:52:04.269512  
**Iterations per operation per style:** 30  
**Environment:** single development machine, real PostgreSQL, real HTTP round trips via a random-port embedded Tomcat (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).

## Results

| Operation | Avg REST (ms) | Avg GraphQL (ms) | P95 REST (ms) | P95 GraphQL (ms) | Tput REST (ops/s) | Tput GraphQL (ops/s) | Faster |
|---|---|---|---|---|---|---|---|
| Get Doctor by id | 24.55 | 40.23 | 105.73 | 46.77 | 41 | 25 | REST (+39.0%) |
| List Doctors (page=0,size=20) | 17.63 | 31.55 | 18.31 | 44.66 | 57 | 32 | REST (+44.1%) |
| Get Patient by id | 16.89 | 19.21 | 61.15 | 39.85 | 59 | 52 | REST (+12.1%) |
| Get Role by id | 13.96 | 103.66 | 18.28 | 134.43 | 72 | 10 | REST (+86.5%) |
| Get Appointment by id | 11.41 | 18.45 | 15.53 | 36.20 | 88 | 54 | REST (+38.2%) |
| Create Doctor | 31.58 | 31.90 | 39.63 | 40.32 | 32 | 31 | REST (+1.0%) |
| Update Doctor | 14.80 | 27.27 | 17.11 | 32.85 | 68 | 37 | REST (+45.7%) |
| Delete Doctor | 11.64 | 17.40 | 14.73 | 29.81 | 86 | 57 | REST (+33.1%) |

## Analysis

- REST was faster (lower avg latency) in 8 of 8 operations measured.
- Across all operations, REST averaged 17.81 ms and GraphQL averaged 36.21 ms per request.
- The largest relative gap was on **Get Role by id**, where REST was faster.
- Both styles call the exact same service-layer method for a given operation (e.g. both `GET /api/v1/roles/{id}` and `query { role(...) }` call `RoleService.getRole`), so the gap measured here is transport/protocol overhead — GraphQL's per-request query parsing, validation, and field-by-field resolution — not database or business-logic cost.
- **Conclusion:** for the fixed, well-known request shapes this project's own frontend sends today, REST's simpler request/response cycle has a real, measurable latency edge over GraphQL on the same data. GraphQL's own advantage — letting a caller request exactly the fields/nesting depth it needs in one round trip — matters more for callers with heterogeneous or deeply-nested field needs (see the live decision reference at `GET /docs/rest-vs-graphql`) than for raw per-request speed on a single fixed shape.

*Generated by `RestVsGraphQlBenchmarkTest.runFullBenchmarkAndWriteReport` — re-run it (after commenting out its `@Disabled`) to regenerate this table.*
