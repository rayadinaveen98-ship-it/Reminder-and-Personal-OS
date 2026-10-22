-- Reminder and Personal OS — portable SQLite schema v1
-- SQLite 3.35+; caller MUST enable PRAGMA foreign_keys=ON on every connection.
-- Never treat an OS scheduling callback as explicit Done/Skipped.
CREATE TABLE reminder_definitions (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL CHECK(length(trim(title)) > 0),
    state TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(state IN ('ACTIVE','PAUSED','ARCHIVED')),
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL
);
CREATE TABLE occurrences (
    id TEXT PRIMARY KEY NOT NULL,
    reminder_id TEXT NOT NULL REFERENCES reminder_definitions(id),
    due_at_ms INTEGER NOT NULL,
    revision INTEGER NOT NULL CHECK(revision > 0),
    logical_slot TEXT NOT NULL,
    outcome TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK(outcome IN ('UNKNOWN','DONE','SKIPPED')),
    acknowledged INTEGER NOT NULL DEFAULT 0 CHECK(acknowledged IN (0,1)),
    snoozed_until_ms INTEGER,
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    UNIQUE(reminder_id,revision,logical_slot),
    CHECK(snoozed_until_ms IS NULL OR outcome='UNKNOWN')
);
CREATE INDEX occurrences_due ON occurrences(due_at_ms);
CREATE INDEX occurrences_reminder ON occurrences(reminder_id,due_at_ms);

-- Action id acts as the idempotency key, across callbacks and device retries.
CREATE TABLE action_events (
    id TEXT PRIMARY KEY NOT NULL,
    occurrence_id TEXT NOT NULL REFERENCES occurrences(id),
    kind TEXT NOT NULL CHECK(kind IN ('DONE','SKIP','SNOOZE','ACKNOWLEDGE','REOPEN')),
    acted_at_ms INTEGER NOT NULL,
    recorded_at_ms INTEGER NOT NULL,
    snooze_until_ms INTEGER,
    CHECK((kind='SNOOZE' AND snooze_until_ms IS NOT NULL) OR
          (kind<>'SNOOZE' AND snooze_until_ms IS NULL))
);
CREATE INDEX action_events_occurrence ON action_events(occurrence_id,recorded_at_ms);

-- One durable job for each requested OS action; scheduler side effects are not
-- atomic with SQLite. Stable occurrence identity makes retried OS calls safe.
CREATE TABLE schedule_outbox (
    id TEXT PRIMARY KEY NOT NULL,
    occurrence_id TEXT NOT NULL REFERENCES occurrences(id),
    operation TEXT NOT NULL CHECK(operation IN ('REGISTER','CANCEL')),
    fire_at_ms INTEGER,
    revision INTEGER NOT NULL CHECK(revision > 0),
    state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','APPLIED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts >= 0),
    last_error TEXT,
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    CHECK((operation='REGISTER' AND fire_at_ms IS NOT NULL) OR
          (operation='CANCEL' AND fire_at_ms IS NULL))
);
CREATE INDEX schedule_outbox_pending ON schedule_outbox(state,created_at_ms);

-- Native handles are disposable projections: never copy them to another device
-- during backup restore. At most one known active native registration per slot.
CREATE TABLE platform_registrations (
    occurrence_id TEXT PRIMARY KEY NOT NULL REFERENCES occurrences(id),
    platform_handle TEXT NOT NULL UNIQUE,
    fire_at_ms INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL
);
PRAGMA user_version = 1;
