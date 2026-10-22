"""SQL-level integration tests for transactions, idempotency and crash recovery."""
from pathlib import Path
import sqlite3
import tempfile
import unittest

from tools.sqlite_reference import Conflict, ReminderStore


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.s = ReminderStore()
        self.s.add(
            reminder_id="r1", occurrence_id="o1", title="Skincare",
            due_ms=2000, slot="2026-10-22", outbox_id="job-1", now_ms=1000,
        )

    def tearDown(self):
        self.s.close()

    def rows(self, table):
        return self.s.db.execute("SELECT * FROM " + table).fetchall()

    def respond(self, aid="a1", kind="DONE", **kw):
        return self.s.action(action_id=aid, occurrence_id="o1", kind=kind,
                             at_ms=2001, now_ms=2100, **kw)

    def test_version_and_foreign_key_enforced(self):
        self.assertEqual(1, self.s.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual(1, self.s.db.execute("PRAGMA foreign_keys").fetchone()[0])

    def test_initial_occurrence_unknown(self):
        self.assertEqual("UNKNOWN", self.s.outcome("o1")["outcome"])
        self.assertEqual(1, len(self.s.pending_operations()))

    def test_terminal_action_creates_cancel_and_supersedes_old_register(self):
        self.assertEqual("APPLIED", self.respond(cancel_job_id="cancel-1"))
        self.assertEqual("DONE", self.s.outcome("o1")["outcome"])
        pending = self.s.pending_operations()
        self.assertEqual(["CANCEL"], [r["operation"] for r in pending])
        original = self.s.db.execute(
            "SELECT state FROM schedule_outbox WHERE id='job-1'").fetchone()[0]
        self.assertEqual("SUPERSEDED", original)

    def test_identical_callback_is_idempotent(self):
        self.respond(cancel_job_id="cancel-1")
        self.assertEqual("DUPLICATE", self.respond(cancel_job_id="cancel-1"))
        self.assertEqual(1, len(self.rows("action_events")))
        self.assertEqual(2, len(self.rows("schedule_outbox")))

    def test_same_action_id_with_changed_payload_rejected(self):
        self.respond(cancel_job_id="cancel-1")
        with self.assertRaises(Conflict):
            self.respond(aid="a1", kind="SKIP", cancel_job_id="cancel-different")
        self.assertEqual("DONE", self.s.outcome("o1")["outcome"])

    def test_new_action_on_resolved_reminder_rejected(self):
        self.respond(cancel_job_id="cancel-1")
        with self.assertRaises(Conflict):
            self.respond(aid="a2", kind="SKIP", cancel_job_id="cancel-2")
        self.assertEqual("DONE", self.s.outcome("o1")["outcome"])
        self.assertEqual(1, len(self.rows("action_events")))

    def test_acknowledge_does_not_mark_done(self):
        self.respond(kind="ACKNOWLEDGE")
        self.assertEqual("UNKNOWN", self.s.outcome("o1")["outcome"])
        self.assertEqual(1, self.s.outcome("o1")["acknowledged"])
        self.assertEqual(1, len(self.s.pending_operations()))

    def test_snooze_creates_cancel_and_new_registration(self):
        self.respond(kind="SNOOZE", snooze_until_ms=6000, cancel_job_id="c1", register_job_id="reg2")
        state = self.s.outcome("o1")
        self.assertEqual("UNKNOWN", state["outcome"])
        self.assertEqual(6000, state["snoozed_until_ms"])
        jobs = self.s.pending_operations()
        self.assertEqual(["CANCEL", "REGISTER"], [r["operation"] for r in jobs])
        self.assertEqual(6000, jobs[1]["fire_at_ms"])
        self.assertEqual("SUPERSEDED", self.s.db.execute(
            "SELECT state FROM schedule_outbox WHERE id='job-1'").fetchone()[0])

    def test_snooze_invalid_target_rolls_back(self):
        with self.assertRaises(Conflict):
            self.respond(kind="SNOOZE", snooze_until_ms=2001, cancel_job_id="c1", register_job_id="reg2")
        self.assertEqual(0, len(self.rows("action_events")))
        self.assertEqual(1, len(self.rows("schedule_outbox")))

    def test_snooze_missing_job_is_rejected(self):
        with self.assertRaises(Conflict):
            self.respond(kind="SNOOZE", snooze_until_ms=6000, cancel_job_id="c1")
        self.assertEqual(0, len(self.rows("action_events")))

    def test_cancel_job_collision_rolls_back_done(self):
        # Occupied outbox key; the event and outcome must remain unchanged.
        with self.assertRaises(sqlite3.IntegrityError):
            self.respond(cancel_job_id="job-1")
        self.assertEqual("UNKNOWN", self.s.outcome("o1")["outcome"])
        self.assertEqual(0, len(self.rows("action_events")))
        self.assertEqual("PENDING", self.s.db.execute(
            "SELECT state FROM schedule_outbox WHERE id='job-1'").fetchone()[0])

    def test_outbox_retry_carries_error_without_claiming_success(self):
        self.s.mark_operation_failed("job-1", "EXACT_ALARM_DENIED", 1010)
        row = self.s.pending_operations()[0]
        self.assertEqual("FAILED", row["state"])
        self.assertEqual("EXACT_ALARM_DENIED", row["last_error"])
        self.assertEqual(1, row["attempts"])
        self.s.mark_operation_applied("job-1", 1110, "os-handle-1")
        reg = self.rows("platform_registrations")[0]
        self.assertEqual("os-handle-1", reg["platform_handle"])
        self.assertEqual(2, self.rows("schedule_outbox")[0]["attempts"])
        self.assertEqual([], self.s.pending_operations())

    def test_outbox_apply_idempotency(self):
        self.assertEqual("APPLIED", self.s.mark_operation_applied("job-1", 1200, "os1"))
        self.assertEqual("DUPLICATE", self.s.mark_operation_applied("job-1", 1400, "os1"))
        self.assertEqual(1, len(self.rows("platform_registrations")))

    def test_superseded_job_cannot_be_applied(self):
        self.respond(cancel_job_id="cancel-1")
        with self.assertRaises(Conflict):
            self.s.mark_operation_applied("job-1", 3000, "invalid")
        self.assertEqual([], self.rows("platform_registrations"))

    def test_terminal_after_register_sends_cancel_and_removes_handle(self):
        self.s.mark_operation_applied("job-1", 1200, "os1")
        self.respond(cancel_job_id="cancel-1")
        self.assertEqual(["CANCEL"], [r["operation"] for r in self.s.pending_operations()])
        self.s.mark_operation_applied("cancel-1", 2200)
        self.assertEqual([], self.rows("platform_registrations"))

    def test_cancellation_does_not_change_user_outcome(self):
        self.s.mark_operation_applied("job-1", 1200, "os1")
        self.assertEqual("UNKNOWN", self.s.outcome("o1")["outcome"])

    def test_reopen_requires_explicit_new_event(self):
        self.respond(cancel_job_id="cancel-1")
        self.respond(aid="a2", kind="REOPEN")
        self.assertEqual("UNKNOWN", self.s.outcome("o1")["outcome"])
        self.assertEqual(2, len(self.rows("action_events")))

    def test_missing_data_is_not_failed_completion(self):
        stats = self.s.summary(0, 3000)
        self.assertEqual(1, stats["unknown"])
        self.assertEqual(0, stats["skipped"])
        self.assertEqual(1.0, stats["unknown_share"])
        self.assertIsNone(stats["known_completion_rate"])

    def test_period_boundaries(self):
        self.assertEqual(0, self.s.summary(2001, 3000)["eligible"])
        self.assertEqual(1, self.s.summary(2000, 3000)["eligible"])
        self.assertIsNone(self.s.summary(2001, 3000)["confirmed_coverage"])

    def test_reject_invalid_period(self):
        with self.assertRaises(Conflict):
            self.s.summary(100, 100)

    def test_foreign_key_rejects_unknown_occurrence(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.s.db.execute(
                """INSERT INTO action_events(id,occurrence_id,kind,acted_at_ms,recorded_at_ms)
                   VALUES('bad','missing','DONE',100,101)"""
            )

    def test_reopen_database_retains_jobs_and_state(self):
        with tempfile.TemporaryDirectory() as td:
            db = Path(td) / "reminders.sqlite"
            a = ReminderStore(db)
            a.add(reminder_id="r", occurrence_id="o", title="Study", due_ms=900,
                  slot="2030-01-01", outbox_id="job", now_ms=100)
            a.mark_operation_failed("job", "OS_UNAVAILABLE", 200)
            a.close()
            b = ReminderStore(db)
            self.assertEqual(1, len(b.pending_operations()))
            self.assertEqual("FAILED", b.pending_operations()[0]["state"])
            self.assertEqual("UNKNOWN", b.outcome("o")["outcome"])
            b.close()

    def test_unsupported_future_version_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            db = Path(td) / "future.sqlite"
            raw = sqlite3.connect(db)
            raw.execute("PRAGMA user_version=77")
            raw.commit()
            raw.close()
            with self.assertRaises(RuntimeError):
                ReminderStore(db)

    def test_duplicate_definition_rolls_back_atomic_creation(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.s.add(reminder_id="r1", occurrence_id="new", title="Dup", due_ms=5000,
                       slot="new", outbox_id="job-new", now_ms=3000)
        self.assertEqual(1, len(self.rows("occurrences")))
        self.assertEqual(1, len(self.rows("schedule_outbox")))

    def test_snooze_job_collision_rolls_back_everything(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.respond(kind="SNOOZE", snooze_until_ms=5000,
                         cancel_job_id="c1", register_job_id="job-1")
        self.assertEqual(0, len(self.rows("action_events")))
        self.assertIsNone(self.s.outcome("o1")["snoozed_until_ms"])
        self.assertEqual(1, len(self.rows("schedule_outbox")))


if __name__ == "__main__":
    unittest.main()
