"""Schema migration contract for Android v0.2 -> v0.3a.

Runs on standard Python SQLite. This is NOT Android SQLiteOpenHelper instrumentation;
we extract the exact ALTER statements from the checked-in Kotlin implementation.
"""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = (ROOT / "androidApp/src/main/java/dev/reminderos/preview/ReminderDatabase.kt").read_text()

def legacy_database():
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    db.executescript("""
    CREATE TABLE reminders(
        id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        due_at INTEGER NOT NULL,
        original_due_at INTEGER NOT NULL,
        revision INTEGER NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('PENDING','DONE','SKIPPED')),
        last_fired_revision INTEGER NOT NULL DEFAULT -1,
        updated_at INTEGER NOT NULL
    );
    CREATE TABLE events(
        id TEXT PRIMARY KEY NOT NULL,
        reminder_id TEXT NOT NULL REFERENCES reminders(id),
        kind TEXT NOT NULL,
        revision INTEGER NOT NULL,
        at_ms INTEGER NOT NULL
    );
    CREATE TABLE schedule_jobs(
        id TEXT PRIMARY KEY NOT NULL,
        reminder_id TEXT NOT NULL REFERENCES reminders(id),
        kind TEXT NOT NULL CHECK(kind IN ('REGISTER','CANCEL')),
        revision INTEGER NOT NULL,
        fire_at INTEGER,
        state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','FAILED','SUPERSEDED')),
        attempts INTEGER NOT NULL DEFAULT 0,
        error TEXT,
        created_at INTEGER NOT NULL
    );
    PRAGMA user_version=1;
    """)
    db.execute("INSERT INTO reminders VALUES('existing','Drink water',1000,1000,2,'PENDING',-1,500)")
    db.execute("INSERT INTO events VALUES('event1','existing','CREATED',1,500)")
    db.execute("INSERT INTO schedule_jobs VALUES('job1','existing','REGISTER',2,1000,'APPLIED',1,NULL,500)")
    db.commit()
    return db

def upgrade(db):
    updates = re.findall(r'db\.execSQL\("(ALTER TABLE reminders ADD COLUMN[^"]+)"\)', SRC)
    updates = [sql for sql in updates if "paused INTEGER" in sql or "archived INTEGER" in sql]
    if len(updates) != 2:
        raise AssertionError("Expected precisely two v0.2-to-v0.3a additive migration statements")
    db.execute("BEGIN")
    try:
        for statement in updates:
            db.execute(statement)
        db.execute("PRAGMA user_version=2")
        db.commit()
    except Exception:
        db.rollback()
        raise

class AndroidMigrationTest(unittest.TestCase):
    def test_additive_update_preserves_existing_reminder(self):
        db=legacy_database()
        try:
            before=db.execute("SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision,updated_at FROM reminders").fetchone()
            upgrade(db)
            after=db.execute("SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision,updated_at FROM reminders").fetchone()
            self.assertEqual(before,after)
            self.assertEqual((0,0),db.execute("SELECT paused,archived FROM reminders").fetchone())
            self.assertEqual(2,db.execute("PRAGMA user_version").fetchone()[0])
        finally: db.close()

    def test_upgrade_preserves_action_and_outbox_history(self):
        db=legacy_database()
        try:
            upgrade(db)
            self.assertEqual(("event1","existing","CREATED",1,500), db.execute("SELECT * FROM events").fetchone())
            self.assertEqual(1,db.execute("SELECT COUNT(*) FROM schedule_jobs WHERE state='APPLIED'").fetchone()[0])
        finally: db.close()

    def test_new_fields_persist_and_are_independent(self):
        db=legacy_database()
        try:
            upgrade(db)
            db.execute("UPDATE reminders SET paused=1 WHERE id='existing'")
            self.assertEqual((1,0),db.execute("SELECT paused,archived FROM reminders").fetchone())
            db.execute("UPDATE reminders SET archived=1 WHERE id='existing'")
            self.assertEqual((1,1),db.execute("SELECT paused,archived FROM reminders").fetchone())
        finally: db.close()

    def test_illegal_flags_rejected_by_check_constraints(self):
        db=legacy_database()
        try:
            upgrade(db)
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute("UPDATE reminders SET paused=2 WHERE id='existing'")
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute("UPDATE reminders SET archived=-1 WHERE id='existing'")
        finally: db.close()

if __name__ == "__main__":
    unittest.main()
