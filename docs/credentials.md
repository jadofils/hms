# Seed credentials

These accounts are created automatically on every application startup by
`DataSeeder` (`src/main/java/amalitech/hospital/management/config/DataSeeder.java`) —
it checks whether each username already exists before creating it, so it's safe to
leave running in every environment rather than being a one-off script.

Dev/demo credentials only — not production secrets. Use them to log in via
`POST /api/v1/auth/login` and try out role-gated behavior once it's wired up.

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | Admin |
| `doctorjohn` | `Doctor@123` | Doctor |
| `receptionist1` | `Reception@123` | Receptionist |
| `analyst1` | `Analyst@123` | Analyst |
| `pharmacist1` | `Pharmacist@123` | Pharmacist |

## What each role can do

See `DataSeeder.ROLE_GRANTS` for the exact permission set granted to each role.

- **Admin** — every permission on every resource (`users`, `roles`, `permissions`,
  `patients`, `doctors`, `departments`, `doctor-schedules`).
- **Doctor** — read patients; read/update their own doctor record; read departments;
  full control over their own `doctor-schedules` (self-service scheduling).
- **Receptionist** — full CRUD on patients; read doctors and departments; read
  doctor-schedules (to check availability).
- **Analyst** — read-only across every resource, no writes.
- **Pharmacist** — read patients and doctors (the pharmacy domain itself has no
  service layer yet).

Note: `SecurityConfig` currently permits all requests regardless of role
(`anyRequest().permitAll()`) — these role/permission grants exist and are queryable via
the `/api/v1/roles`/`/api/v1/permissions` endpoints, but nothing enforces them against a
request yet.
