# Custom Annotations Reference

This project has seven custom annotations, each following the same convention: **never
call the underlying mechanism directly — annotate a method and call *that*; a dedicated
`@Aspect` (or, for the two that register brand-new periodic/event-driven work rather than
intercepting an existing call, a `BeanPostProcessor`) does the real work.** The annotated
method's entire body is either `throw new IllegalStateException("... did not intercept
this call")` (for the four AOP-intercepted ones) or a real implementation the mechanism
just discovers and wires up (for `@ScheduledMaintenance`/`@Subscribe`, since there's no
"real" body to run instead — the method itself *is* the scheduled task / event listener).

**Self-injection.** Spring AOP proxies only intercept calls made *through the proxy* — a
method calling another method on `this` bypasses the proxy entirely and the aspect never
fires. So every AOP-intercepted annotated method lives on the service that needs it, but
every *other* method on that service calls it through a self-injected `@Lazy` proxy
reference (`self`), never as `this.method(...)`. See `AlgorithmAspect`'s section below for
the full shape; every other AOP-intercepted annotation in this document follows it
identically.

| Annotation | Applies to | Intercepted/processed by |
|---|---|---|
| [`@RequirePermission`](#requirepermission) | Controller methods | `AuthorizationAspect` |
| [`@ApplyAlgorithm`](#applyalgorithm) | Service methods (sort/search) | `AlgorithmAspect` |
| [`@FindUserData`](#finduserdata) | Service methods (native queries) | `FindUserDataAspect` |
| [`@SqlQueryBuilder`](#sqlquerybuilder) | Service methods (analytics queries) | `SqlQueryBuilderAspect` |
| [`@SendTemplatedEmail`](#sendtemplatedemail) | `MailService` methods | `EmailAspect` |
| [`@ScheduledMaintenance`](#scheduledmaintenance) | Service methods (periodic jobs) | `ScheduledMaintenanceRegistrar` (`BeanPostProcessor`) |
| [`@Subscribe`](#subscribe) | Service methods (event listeners) | `EventBus` (`@PostConstruct` scan) |

---

## `@RequirePermission`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    Resource resource();
    PermissionAction action();
}
```

Declares the `resource:action` permission a caller's role must currently hold to reach the
annotated **controller** method — the only one of the seven that annotates a controller
rather than a service method, since authorization has to run before the request reaches
any business logic at all.

**Intercepted by `AuthorizationAspect`**, which checks `resource:action` against the
caller's role via the `Role`/`Permission`/`RolePermission` tables and rejects (403) before
the controller method body — and therefore the service/DB layer — ever runs.

```java
@GetMapping
@RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.READ)
public ResponseEntity<ApiResult<PagedModel<NotificationResponse>>> getNotifications(Pageable pageable) { ... }
```

`Resource` is a closed enum (not a raw string) specifically so a typo'd resource name
can't silently deny everyone forever with no compile-time signal — see `enums/Resource.java`.
Every `Resource` value is auto-seeded, alongside every `PermissionAction`, into the
`permissions` table by `DataSeeder`; the `Admin` role is granted every resulting
permission automatically, so adding a new `Resource` constant needs no `DataSeeder` change
to make it usable.

**Where in the codebase**: `annotation/RequirePermission.java`, `aop/AuthorizationAspect.java`,
`enums/Resource.java`, `enums/PermissionAction.java`. Tested via `AuthorizationAspectTest`
(needs a real Spring AOP proxy — see Testing note below).

---

## `@ApplyAlgorithm`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplyAlgorithm {
    String value();          // "mergeSort" | "binarySearch"
    String key() default ""; // unused by the aspect itself — see below
}
```

Dynamic values (the list, comparator, key extractor) come from the **method's own runtime
arguments**, read via `ProceedingJoinPoint.getArgs()` in `AlgorithmAspect` — never from
annotation attributes, since attribute values are fixed at compile time and can never
carry something a caller only knows at request time. `key()` exists on the annotation but
is not read by the aspect; the actual key extractor is always the method's own
`Function`/`Comparator` argument.

**Sorting** — mergeSort, in place, returns the same list reference. The self-injection
shape every AOP-intercepted annotation in this document follows:

```java
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    /** @Lazy breaks the circular dependency a self-reference field creates at bean-creation time. */
    @Lazy
    private final RoleService self;

    public List<PermissionResponse> getRolePermissions(String roleId) {
        List<PermissionResponse> permissions = /* ...load... */;
        return self.sort(permissions, Comparator.comparing(RoleService::permissionKey)); // NOT this.sort(...)
    }

    @ApplyAlgorithm("mergeSort")
    public <T> List<T> sort(List<T> list, Comparator<T> comparator) {
        throw new IllegalStateException("AlgorithmAspect did not intercept this call");
    }
}
```
`list` must be **mutable** (`AlgorithmUtils.mergeSort` calls `list.set(...)` internally) —
wrap a `Stream.toList()`/`List.of()` result in `new ArrayList<>(...)` first if needed.

**Searching** — `binarySearch` over an *already-sorted* list, returns the index or `-1`:
```java
@ApplyAlgorithm("binarySearch")
public <T> int search(List<T> list, Object targetKey, Function<T, ?> keyExtractor) {
    throw new IllegalStateException("AlgorithmAspect did not intercept this call");
}
```
The list must already be sorted by the same key `keyExtractor` produces — binary search on
an unsorted list gives a meaningless result, not an error. `sort`/`search` are generic
(`<T>`) but each lives on the one service that needs it rather than a shared bean; a new
domain that needs the same in-memory sort/search adds its own small entry point.

**Where in the codebase**: `annotation/ApplyAlgorithm.java`, `aop/AlgorithmAspect.java`,
`utils/AlgorithmUtils.java` (the actual `mergeSort`/`binarySearch` implementations).
Tested via `AlgorithmAspectTest` + `AlgorithmUtilsTest`.

---

## `@FindUserData`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FindUserData {
    String userId() default "";
    String username() default "";
    String domain() default "user"; // "user" | "role" | "permission" | "appointment" | "doctor"
}
```

Builds a native SQL query with the fluent `QueryBuilder` and runs it via
`EntityManager.createNativeQuery`, based on `domain()`. Only `domain="user"` is wired to a
real caller today (`UserService.findUsersPage`, called internally as
`self.findUsersPage(...)`, returning a `PagedRawResult(rows, total)` when the method also
declares `(int page, int size)` args) — the `role`/`appointment`/`doctor` cases exist as
documented-but-uncalled infrastructure; double-check their `SELECT`/`JOIN` columns still
match the current schema before wiring a new caller to them.

**`userId()`/`username()` string-concatenate directly into the SQL** (not parameter
binding) — any value that could reach those annotation attributes from user input needs
sanitizing first.

**Frontend-driven column sort**: when the paginated shape's `(int page, int size)` args
are followed by `(String sortBy, String sortDir)`, `FindUserDataAspect` orders the query
by whichever column `sortBy` names — but only after resolving it against that domain's own
`selectColumnsFor` list (case/underscore-insensitively), falling back to the first selected
column for an unrecognized/missing `sortBy` rather than leaving the query unordered
(Postgres gives no row-order guarantee at all without an `ORDER BY`). This whitelist-via-
existing-SELECT-list is what makes it safe to take `sortBy`/`sortDir` straight off a
request — a raw column name is never concatenated in directly the way `userId()`/
`username()` are.

```java
@FindUserData(domain = "user")
public PagedRawResult findUsersPage(int page, int size, String sortBy, String sortDir) {
    throw new IllegalStateException("FindUserDataAspect did not intercept this call");
}
```

**Where in the codebase**: `annotation/FindUserData.java`, `aop/FindUserDataAspect.java`,
`utils/filters/QueryBuilder.java`. Tested via `FindUserDataAspectTest` + `QueryBuilderTest`.

---

## `@SqlQueryBuilder`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlQueryBuilder {
    String value() default "";     // e.g. "findDoctorsByDepartment"
    String from() default "";
    String[] select() default {};
    String[] where() default {};
    String[] joins() default {};
}
```

Same shape as `@FindUserData` but for one-off analytics queries (typically a `GROUP BY` +
`COUNT`) that don't take per-call filters — `value()` just selects a hardcoded query from
a `switch` in `SqlQueryBuilderAspect`. `from()`/`select()`/`where()`/`joins()` exist on the
annotation but aren't read dynamically by the aspect for the same reason as
`@ApplyAlgorithm`'s unused `key()` — there's no way to parameterize a new query without
adding a case to the aspect's `switch`.

None of the three cases (`findDoctorsByDepartment`, `findDepartmentsWithDoctors`,
`findRolesWithPermissionCount`) has a real caller today; copy this shape for the next real
one (self-injected method calling `self.yourMethod()`, annotated
`@SqlQueryBuilder("yourCase")`). If you need caller-supplied filters instead of a fixed
query, `@FindUserData`'s `userId()`/`username()` pattern (or the runtime-args trick
`@ApplyAlgorithm`/`@FindUserData` both use) is the closer fit, not this one.

**Deliberately not backed by an `ORDER BY`** — `QueryBuilder` has an `orderBy(column[, dir])`
method, but neither `FindUserDataAspect` nor `SqlQueryBuilderAspect` calls it; sort in
memory with `self.sort` (see `@ApplyAlgorithm`) immediately before anything that depends
on order, every time.

**Where in the codebase**: `annotation/SqlQueryBuilder.java`, `aop/SqlQueryBuilderAspect.java`.
Tested via `SqlQueryBuilderAspectTest`.

---

## `@SendTemplatedEmail`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SendTemplatedEmail {
    String value(); // "otp" | "passwordReset" | "passwordChanged" | "generic" |
                     // "emailVerification" | "accountCreated"
}
```

Marks a `MailService` method as an HTML-templated email send, intercepted and fully
executed by `EmailAspect`: `value()` selects both which `templates/email/*.html` file to
render and how the annotated method's own runtime arguments map onto that template's
placeholders. `EmailAspect` renders the template (a simple `{{placeholder}}` substitution,
not a full template engine) and dispatches it via `JavaMailSender`.

```java
@SendTemplatedEmail("emailVerification")
public void sendEmailVerificationEmail(String toEmail, String username, String verifyUrl, long expiryHours) {
    throw new IllegalStateException("EmailAspect did not intercept this call");
}
```

Every case is a real, wired-up email a service actually sends (unlike `@SqlQueryBuilder`'s
uncalled cases) — `otp`/`passwordReset`/`passwordChanged`/`generic` from earlier auth work,
plus `emailVerification` (self-registration's verify-link email) and `accountCreated`
(an admin-provisioned account's generated-password email) added alongside those two
features. Adding a new case means: add the template HTML file, add the `MailService`
method (body throws, per convention), add the matching `case` in `EmailAspect`.

**Where in the codebase**: `annotation/SendTemplatedEmail.java`, `aop/EmailAspect.java`,
`service/MailService.java`, `resources/templates/email/*.html`. Tested via `EmailAspectTest`.

---

## `@ScheduledMaintenance`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledMaintenance {
    String value();             // a short id, e.g. "log-cleanup"
    long interval();
    MaintenanceInterval unit(); // HOURS | DAYS
}
```

The first of the two annotations in this document that isn't `@Aspect`-intercepted.
`interval()`/`unit()` control **run frequency only** — the actual retention/idle
thresholds used *inside* the annotated method are separate `@Value`-injected fields on the
service, since "how often do we check" and "what's the cutoff" are independent knobs that
just happen to often share a similar magnitude.

**Processed by `ScheduledMaintenanceRegistrar`, a `BeanPostProcessor`** — the correct
Spring extension point for *registering new periodic invocations*, as opposed to an
`@Around`/`@Before` `@Aspect`, which only ever fires when *something else calls* the
annotated method. This is genuinely a different job: creating new periodic invocations
nothing else triggers, mirroring how Spring's own `ScheduledAnnotationBeanPostProcessor`
(backing `@Scheduled`) works internally. At `postProcessAfterInitialization`, it reflects
over each bean's methods for `@ScheduledMaintenance` and registers each with an injected
`TaskScheduler` (`scheduleAtFixedRate`, period derived from `interval()`/`unit()`).

```java
@ScheduledMaintenance(value = "log-cleanup", interval = 1, unit = MaintenanceInterval.DAYS)
public void cleanupOldLogs() {
    systemLogRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(logRetentionDays));
}

@ScheduledMaintenance(value = "deactivate-idle-users", interval = 6, unit = MaintenanceInterval.HOURS)
public void deactivateIdleUsers() {
    userRepository.findActiveUsersIdleSince(LocalDateTime.now().minusDays(idleUserDays))
            .forEach(u -> { u.setIsActive(false); u.setUpdatedAt(now); userRepository.save(u); });
}
```
Unlike the four `@Aspect`-intercepted annotations, **the method body here is real** —
there's no "did not intercept this call" placeholder, since the registrar discovers and
schedules the method rather than replacing what it does.

**A real CGLIB-proxy pitfall this was designed around**: `MaintenanceService` (like every
`@Service`) is wrapped in a CGLIB proxy by `LoggingAspect`'s blanket service-layer
pointcut. Reflecting on `bean.getClass()` inside the registrar would *not* find
`@ScheduledMaintenance` on the proxy's override methods — a generated proxy subclass's
overriding methods don't automatically carry the original method's annotations under plain
reflection. Fixed by resolving the real class via `AopUtils.getTargetClass(bean)` before
reflecting, while still invoking the method on the actual (possibly proxied) `bean` so
other cross-cutting advice (logging, transactions) still applies.

**Where in the codebase**: `annotation/ScheduledMaintenance.java`,
`aop/ScheduledMaintenanceRegistrar.java`, `enums/MaintenanceInterval.java`,
`config/SchedulingConfig.java` (the `TaskScheduler` bean), `service/MaintenanceService.java`.
Tested via `ScheduledMaintenanceRegistrarTest` (manually-constructed Mockito unit test —
no real proxy needed to exercise a plain `BeanPostProcessor` method) + `MaintenanceServiceTest`.

---

## `@Subscribe`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
    String name();     // stable id for the runtime subscribe/unsubscribe toggle
    Class<?> event();
}
```

Marks a method as a listener for a domain event. **Processed by `EventBus`**, an in-house
pub-sub registry — deliberately not built on Spring's own
`ApplicationEventPublisher`/`ApplicationListener`, which has no built-in mechanism to
toggle a single listener off/on at runtime, the exact thing
`EventSubscriptionController`'s subscribe/unsubscribe endpoints need.

At `@PostConstruct`, `EventBus` walks every bean in the context for `@Subscribe`-annotated
methods — the same "walk every bean's methods for the annotation" idea as
`ScheduledMaintenanceRegistrar`, just triggered once from inside `EventBus` over every bean
up front, rather than a `BeanPostProcessor` intercepting each bean as it's created.
Resolves each bean's real class via `AopUtils.getTargetClass` on the concrete
(already-created) bean instance — the same CGLIB-proxy pitfall `@ScheduledMaintenance`
avoids, and for the same reason.

```java
@Subscribe(name = "notification-on-appointment-created", event = AppointmentCreatedEvent.class)
public void onAppointmentCreated(AppointmentCreatedEvent event) {
    Appointment appointment = event.appointment();
    notificationService.createNotification(/* ...build request from appointment... */);
}
```

`name()` must be unique across the whole application — it's the id an admin passes to
`POST /api/v1/events/{name}/subscribe|unsubscribe` to toggle that one listener, and the
same id shows up in `GET /api/v1/events`'s listing. `EventBus.publish(Object event)`
invokes every currently-*enabled* subscriber registered for `event`'s exact class; a
subscriber's own failure is logged and swallowed so one broken listener can never break
the publishing call site's real work or any other subscriber for the same event.

Publishers call `eventBus.publish(new SomeEvent(...))` right after `save(...)` in a
create method — see `AppointmentService.createAppointment`,
`PrescriptionService.createPrescription`, `LabResultService.createResult`,
`InvoiceService.createInvoice`. `NotificationEventListener` is the one listener class
registered today, turning each of those four events into a `Notification` via
`NotificationService.createNotification`.

**Where in the codebase**: `annotation/Subscribe.java`, `aop/EventBus.java`,
`event/*Event.java` (the four event records), `service/NotificationEventListener.java`,
`controller/EventSubscriptionController.java`. Tested via `EventBusTest` (manually-
constructed, mocked `ApplicationContext` — no real proxy needed, same reasoning as
`ScheduledMaintenanceRegistrarTest`) + `NotificationEventListenerTest` +
`EventSubscriptionControllerTest`.

---

## Testing note

Per CLAUDE.md's Testing section: the self-injected `self` field is mocked as another
collaborator at the same boundary as any other Mockito unit test — `@Aspect`-driven
methods (`sort`, `findUsersPage`, etc.) are stubbed on it, never exercised for real,
because the aspects themselves need a real Spring AOP proxy to test properly. That's why
`AuthorizationAspectTest`, `AlgorithmAspectTest`, `FindUserDataAspectTest`,
`SqlQueryBuilderAspectTest`, and `EmailAspectTest` are `@SpringBootTest`-based, while
`ScheduledMaintenanceRegistrarTest` and `EventBusTest` are plain Mockito unit tests — a
`BeanPostProcessor`'s `postProcessAfterInitialization` and a plain `@PostConstruct` method
are both ordinary method calls a manually-constructed instance can exercise directly,
with no real Spring proxy required.
