# Story 4.1 — GraphQL Integration

> As a frontend developer, I want to fetch data using GraphQL queries and mutations.

## Acceptance criteria

- GraphQL schema defined for core entities
- Queries and mutations implemented
- REST and GraphQL coexist without conflict

## Status: not started — deferred by explicit request

This epic is intentionally the last piece of work left, deferred until every REST-facing
story was closed out first.

## Current state (as of this pass)

The dependency and servlet mapping are already present but genuinely inert:

- `pom.xml` has the GraphQL Java Tools dependency.
- `application.yaml` configures the *server* side:
  ```yaml
  graphql:
    tools:
      schema-location-pattern: "**/*.graphqls"
    servlet:
      mapping: /graphql
      enabled: true
      cors-enabled: true
      exception-handlers-enabled: true
  ```
- `graphqlcodegen-maven-plugin` is configured to generate *client* code from schema
  files under `src/main/resources/graphql-client/**/*.graphqls` — that directory doesn't
  exist, so it's a no-op.
- **No `.graphqls` schema file exists anywhere in the repo** — `graphql.tools.schema-
  location-pattern` has nothing to match, so despite the dependency and servlet mapping
  being configured, **no GraphQL endpoint is actually being served**. Hitting
  `POST /graphql` today returns whatever the servlet's default no-schema behavior is, not
  a working query/mutation surface.

## What closing this story will need

1. Author `.graphqls` schema file(s) under `src/main/resources/graphql/` covering the
   "core entities" — at minimum `User`/`Role`/`Permission`/`Patient`/`Doctor`/
   `Department`/`DoctorSchedule`/`Appointment`, matching the shapes their REST
   `*Response` DTOs already expose.
2. `@Component`-annotated GraphQL resolvers (query/mutation root resolvers) that
   delegate to the *same* existing services (`PatientService`, `DoctorService`, etc.) —
   REST and GraphQL should share one service layer, not duplicate business logic.
3. Verify REST and GraphQL genuinely coexist: both `/api/v1/**` and `/graphql` reachable
   in the same running app, exercised by both Swagger UI and a GraphQL client
   (e.g. GraphiQL/Postman) without one breaking the other.
4. The README's own Deliverables table ties a "REST vs GraphQL" performance report to
   this epic (see Story 2.2's doc) — write that once both surfaces exist to compare.

## Where in the codebase (current, inert state)

- `pom.xml` — GraphQL dependency/codegen plugin config.
- `src/main/resources/application.yaml` — `graphql:` block.
- `src/main/resources/graphql/`, `src/main/resources/graphql-client/` — empty
  directories referenced by config, holding no schema files yet.
