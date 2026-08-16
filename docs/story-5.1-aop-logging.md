# Story 5.1 — Cross-Cutting Concerns (AOP)

> As a developer, I want centralized logging and monitoring using AOP.

## Acceptance criteria

- `@Before`, `@After`, `@Around` aspects implemented
- Logging applied to service layer methods
- AOP behavior documented

## How it was achieved

**Gap found during the audit.** Four `@Aspect` classes already existed
(`AlgorithmAspect`, `FindUserDataAspect`, `SqlQueryBuilderAspect`, `EmailAspect`), but
every one of them is narrow-purpose — sorting-algorithm selection, native-SQL query
building, email sending — none of them is a *general* logging/monitoring aspect over the
service layer. This story added one: `aop/LoggingAspect.java`.

**One pointcut, three advice types, each a distinct concern** (not three aspects logging
the same event redundantly):

```java
@Pointcut("execution(* amalitech.hospital.management.service..*(..))")
public void serviceLayer() {}

@Before("serviceLayer()")
public void logEntry(JoinPoint jp) { ... }     // "→ Class.method() [N arg(s)]"  (INFO)

@After("serviceLayer()")
public void logExit(JoinPoint jp) { ... }      // "← Class.method() completed"  (DEBUG)
                                                // runs on success AND failure alike —
                                                // this is the "finally" advice type

@Around("serviceLayer()")
public Object logTiming(ProceedingJoinPoint jp) throws Throwable { ... }
    // "✓ Class.method() finished in Nms"       (INFO, on success)
    // "✗ Class.method() failed after Nms: msg" (ERROR, on exception, rethrown unchanged)
```
`@Around` is the only advice type with access to timing, the return value, *and* a
thrown exception, so it owns both the success and failure timing logs; `@Before`/`@After`
each own one distinct, simpler concern instead of duplicating what `@Around` already
does.

**Deliberately never logs raw argument or return values.** Service method parameters
include DTOs like `UserRequest`/`ChangePasswordRequest` (plaintext passwords) and
`PatientRequest`/`AppointmentRequest` (patient PII) — every one of them is a Lombok
`@Data` class, so its generated `toString()` would otherwise dump those values straight
into the application log the instant anything here logged an argument or return value.
Only class/method name, argument *count*, elapsed time, and exception *message* are ever
logged.

**A real Spring AOP ordering gotcha, hit and fixed during this work.** The first attempt
ordered `LoggingAspect` at `@Order(Ordered.HIGHEST_PRECEDENCE)` (`Integer.MIN_VALUE`), on
the reasoning that it should always wrap outermost — necessary because
`AlgorithmAspect`/`FindUserDataAspect`/`SqlQueryBuilderAspect` don't declare any order at
all (all default to `LOWEST_PRECEDENCE`) *and* each one replaces its annotated method's
execution instead of calling `proceed()` all the way to the real body; if `LoggingAspect`
ended up nested *inside* one of them, its advice would never run for that call at all.
That reasoning was correct — but the literal `HIGHEST_PRECEDENCE` constant broke context
startup entirely:
```
java.lang.IllegalStateException: No MethodInvocation found: Check that an AOP invocation
is in progress and that the ExposeInvocationInterceptor is upfront in the interceptor
chain. Specifically, note that advices with order HIGHEST_PRECEDENCE will execute before
ExposeInvocationInterceptor!
```
`HIGHEST_PRECEDENCE` pushes an aspect ahead of Spring's own internal
`ExposeInvocationInterceptor`, which `@Before`/`@After` advice on a *named* pointcut
(`serviceLayer()`, not an inline `execution(...)` expression) relies on internally for
join-point matching. The fix was `@Order(1)` instead — still low enough to wrap outside
every other (unordered, `LOWEST_PRECEDENCE`) aspect in this codebase, without hitting the
literal-minimum edge case. Verified by a full `@SpringBootTest` context load after the
fix (see Verification below) — this class of bug only surfaces at actual bean-creation
time, not at compile time.

## Where in the codebase

- `aop/LoggingAspect.java` — the aspect itself; its class-level Javadoc documents the
  reasoning above (the "AOP behavior documented" criterion is satisfied at the code
  level, matching how the other four aspects are already documented, rather than a
  separate markdown writeup).

## Verification

```bash
./mvnw test
```
Tail the log during any test run (or `spring-boot:run` + a few requests) — every service
call produces a `→ Class.method() [...]` / `← Class.method() completed` /
`✓ Class.method() finished in Nms` triplet, with `✗ ... failed after Nms: <message>`
instead of the last line when an exception is thrown. Confirm no request body, password,
or patient field value ever appears in any of these lines — only the message string.
