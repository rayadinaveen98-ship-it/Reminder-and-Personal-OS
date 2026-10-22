"""Executable SQLite protocol reference, not the Kotlin production repository.

Models the transaction boundary that Android and iOS stores must preserve.
Native scheduler calls are external side effects and must be idempotent/retried.
"""
from contextlib import contextmanager
from pathlib import Path
import sqlite3

SCHEMA = Path(__file__).resolve().parents[1] / "storage" / "schema" / "v1.sql"

class Conflict(ValueError):
    pass

class ReminderStore:
    def __init__(self, path=":memory:"):
        self.db = sqlite3.connect(str(path), isolation_level=None)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute("PRAGMA busy_timeout=5000")
        version = self.db.execute("PRAGMA user_version").fetchone()[0]
        if version == 0:
            self.db.executescript(SCHEMA.read_text(encoding="utf-8"))
        elif version != 1:
            raise RuntimeError(f"Unsupported schema version: {version}")

    def close(self):
        self.db.close()

    @contextmanager
    def transaction(self):
        self.db.execute("BEGIN IMMEDIATE")
        try:
            yield
            self.db.execute("COMMIT")
        except BaseException:
            self.db.execute("ROLLBACK")
            raise

    def add(self, *, reminder_id, occurrence_id, title, due_ms, slot, outbox_id, now_ms):
        """Save definition, occurrence and registration intention atomically."""
        if not all((reminder_id, occurrence_id, title.strip(), slot, outbox_id)):
            raise Conflict("Blank identity/title")
        with self.transaction():
            self.db.execute(
                "INSERT INTO reminder_definitions(id,title,created_at_ms,updated_at_ms) VALUES(?,?,?,?)",
                (reminder_id, title, now_ms, now_ms),
            )
            self.db.execute(
                """INSERT INTO occurrences
                   (id,reminder_id,due_at_ms,revision,logical_slot,created_at_ms,updated_at_ms)
                   VALUES(?,?,?,1,?,?,?)""",
                (occurrence_id, reminder_id, due_ms, slot, now_ms, now_ms),
            )
            self.db.execute(
                """INSERT INTO schedule_outbox
                   (id,occurrence_id,operation,fire_at_ms,revision,created_at_ms,updated_at_ms)
                   VALUES(?,?,'REGISTER',?,1,?,?)""",
                (outbox_id, occurrence_id, due_ms, now_ms, now_ms),
            )

    def outcome(self, occurrence_id):
        return self.db.execute(
            "SELECT outcome,acknowledged,snoozed_until_ms FROM occurrences WHERE id=?",
            (occurrence_id,),
        ).fetchone()

    def action(self, *, action_id, occurrence_id, kind, at_ms, now_ms,
               snooze_until_ms=None, cancel_job_id=None, register_job_id=None):
        """Atomically commit response event + current state + cancel intention.

        Important: unknown/no response is not a user action or terminal failure.
        """
        if not action_id or not occurrence_id:
            raise Conflict("Action and occurrence IDs required")
        if kind not in {"DONE","SKIP","SNOOZE","ACKNOWLEDGE","REOPEN"}:
            raise Conflict("Invalid action")
        with self.transaction():
            if self.db.execute("SELECT 1 FROM action_events WHERE id=?", (action_id,)).fetchone():
                return "DUPLICATE"
            row = self.outcome(occurrence_id)
            if row is None:
                raise Conflict("Unknown occurrence")
            previous = row["outcome"]
            if kind in {"DONE","SKIP","SNOOZE","ACKNOWLEDGE"} and previous != "UNKNOWN":
                raise Conflict("Already resolved")
            if kind == "REOPEN" and previous == "UNKNOWN":
                raise Conflict("Already unresolved")
            if kind == "SNOOZE":
                if snooze_until_ms is None or snooze_until_ms <= at_ms:
                    raise Conflict("Invalid snooze target")
            elif snooze_until_ms is not None:
                raise Conflict("Unexpected snooze time")
            if kind in {"DONE","SKIP","SNOOZE"} and not cancel_job_id:
                raise Conflict("Resolving or snoozing requires cancellation outbox ID")
            if kind == "SNOOZE" and not register_job_id:
                raise Conflict("Snooze requires new registration job ID")
            if kind != "SNOOZE" and register_job_id is not None:
                raise Conflict("Unexpected registration job")
            next_outcome = {
                "DONE": "DONE", "SKIP": "SKIPPED", "REOPEN": "UNKNOWN"
            }.get(kind, previous)
            ack = 1 if kind == "ACKNOWLEDGE" else row["acknowledged"]
            new_snooze = snooze_until_ms if kind == "SNOOZE" else (
                None if kind in {"DONE","SKIP","REOPEN"} else row["snoozed_until_ms"]
            )
            self.db.execute(
                """INSERT INTO action_events(id,occurrence_id,kind,acted_at_ms,recorded_at_ms,snooze_until_ms)
                   VALUES(?,?,?,?,?,?)""",
                (action_id, occurrence_id, kind, at_ms, now_ms, snooze_until_ms),
            )
            self.db.execute(
                "UPDATE occurrences SET outcome=?,acknowledged=?,snoozed_until_ms=?,updated_at_ms=? WHERE id=?",
                (next_outcome, ack, new_snooze, now_ms, occurrence_id),
            )
            if kind in {"DONE","SKIP","SNOOZE"}:
                # Retire unexecuted register requests before a cancellation or
                # revised snooze can be processed by the OS executor.
                self.db.execute(
                    """UPDATE schedule_outbox SET state='SUPERSEDED',updated_at_ms=?
                       WHERE occurrence_id=? AND operation='REGISTER'
                         AND state IN ('PENDING','FAILED')""",
                    (now_ms, occurrence_id),
                )
                revision = self.db.execute(
                    "SELECT revision FROM occurrences WHERE id=?", (occurrence_id,)
                ).fetchone()[0]
                self.db.execute(
                    """INSERT INTO schedule_outbox
                       (id,occurrence_id,operation,revision,created_at_ms,updated_at_ms)
                       VALUES(?,?,'CANCEL',?,?,?)""",
                    (cancel_job_id, occurrence_id, revision, now_ms, now_ms),
                )
                if kind == "SNOOZE":
                    self.db.execute(
                        """INSERT INTO schedule_outbox
                           (id,occurrence_id,operation,fire_at_ms,revision,
                            created_at_ms,updated_at_ms)
                           VALUES(?,?,'REGISTER',?,?,?,?)""",
                        (register_job_id, occurrence_id, snooze_until_ms,
                         revision, now_ms, now_ms),
                    )
            return "APPLIED"

    def pending_operations(self):
        # CANCEL before REGISTER for a given occurrence. Caller must additionally
        # reconcile superseded PENDING registers before executing older jobs.
        return self.db.execute(
            """SELECT * FROM schedule_outbox
               WHERE state IN ('PENDING','FAILED')
               ORDER BY CASE operation WHEN 'CANCEL' THEN 0 ELSE 1 END, created_at_ms,id"""
        ).fetchall()

    def mark_operation_failed(self, op_id, error_code, now_ms):
        if not error_code:
            raise Conflict("Error code required")
        with self.transaction():
            cur = self.db.execute(
                """UPDATE schedule_outbox SET state='FAILED',attempts=attempts+1,
                   last_error=?,updated_at_ms=? WHERE id=? AND state <> 'APPLIED'""",
                (error_code, now_ms, op_id),
            )
            if cur.rowcount != 1:
                raise Conflict("Operation absent/already applied")

    def mark_operation_applied(self, op_id, now_ms, native_handle=None):
        """Record observed OS acceptance and native handle atomically after native call.

        Crash between OS call and DB transaction remains recoverable only if
        native schedule/unschedule is idempotent under the same stable identifier.
        """
        with self.transaction():
            row = self.db.execute(
                "SELECT * FROM schedule_outbox WHERE id=?", (op_id,)
            ).fetchone()
            if row is None:
                raise Conflict("Unknown operation")
            if row["state"] == "APPLIED":
                return "DUPLICATE"
            if row["state"] == "SUPERSEDED":
                raise Conflict("Superseded operation cannot be applied")
            if row["operation"] == "REGISTER":
                if not native_handle:
                    raise Conflict("Registration requires native handle")
                self.db.execute(
                    """INSERT INTO platform_registrations
                       (occurrence_id,platform_handle,fire_at_ms,revision,updated_at_ms)
                       VALUES(?,?,?,?,?)
                       ON CONFLICT(occurrence_id) DO UPDATE SET
                          platform_handle=excluded.platform_handle,
                          fire_at_ms=excluded.fire_at_ms,
                          revision=excluded.revision,
                          updated_at_ms=excluded.updated_at_ms""",
                    (row["occurrence_id"], native_handle, row["fire_at_ms"],
                     row["revision"], now_ms),
                )
            else:
                self.db.execute(
                    "DELETE FROM platform_registrations WHERE occurrence_id=?",
                    (row["occurrence_id"],),
                )
            self.db.execute(
                """UPDATE schedule_outbox SET state='APPLIED',attempts=attempts+1,
                   last_error=NULL,updated_at_ms=? WHERE id=?""", (now_ms, op_id)
            )
            return "APPLIED"

    def summary(self, start_ms, end_ms):
        if end_ms <= start_ms:
            raise Conflict("Invalid window")
        rows = self.db.execute(
            """SELECT outcome,COUNT(*) AS n FROM occurrences
               WHERE due_at_ms >= ? AND due_at_ms < ?
               GROUP BY outcome""", (start_ms, end_ms)
        ).fetchall()
        counts = {r["outcome"]: r["n"] for r in rows}
        done = counts.get("DONE", 0)
        skip = counts.get("SKIPPED", 0)
        unknown = counts.get("UNKNOWN", 0)
        total = done + skip + unknown
        return {
            "eligible": total, "done": done, "skipped": skip, "unknown": unknown,
            "confirmed_coverage": done / total if total else None,
            "known_completion_rate": done / (done + skip) if done + skip else None,
            "unknown_share": unknown / total if total else None,
        }
