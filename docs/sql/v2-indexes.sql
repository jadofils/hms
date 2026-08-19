-- Indexes added for HMS v2 (see docs/v2-report.md, Epic 3.2 — "Optimize searching,
-- filtering, and retrieval logic").
--
-- These are declared on the entities themselves via @Table(indexes = {...}) — see
-- Appointment/Notification/Invoice/SystemLog. That annotation only actually creates the
-- index when Hibernate runs DDL, which per application-{profile}.yaml only happens with
-- spring.jpa.hibernate.ddl-auto: update — true in "dev", but "test"/"prod" use
-- "validate" (the existing schema is assumed already correct; Hibernate only checks
-- columns/types exist, never creates or checks for indexes). This project has no
-- Flyway/Liquibase migration tool, so there's no automatic way to get these indexes
-- onto a database that was never started with ddl-auto=update. Run this file by hand
-- against any such database (test/prod, or a dev database created before this file
-- existed) to bring it in line with what the entities now declare.
--
-- IF NOT EXISTS makes this safe to re-run.

CREATE INDEX IF NOT EXISTS idx_appointments_doctor_id ON appointments (doctor_id);
CREATE INDEX IF NOT EXISTS idx_appointments_appointment_date ON appointments (appointment_date);

CREATE INDEX IF NOT EXISTS idx_notifications_read_at ON notifications (read_at);

CREATE INDEX IF NOT EXISTS idx_invoices_payment_status ON invoices (payment_status);

CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON system_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_system_logs_log_level ON system_logs (log_level);
