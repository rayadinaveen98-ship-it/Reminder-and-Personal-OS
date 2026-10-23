"""Validate additive v2 -> v3 recurrence migration against legacy SQLite data."""
import re
import sqlite3
import unittest
from pathlib import Path

SRC=(Path(__file__).resolve().parents[1]/
     "androidApp/src/main/java/dev/reminderos/preview/ReminderDatabase.kt").read_text()

class RecurringMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db=sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript("""
        CREATE TABLE reminders(
            id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL,
            due_at INTEGER NOT NULL, original_due_at INTEGER NOT NULL,
            revision INTEGER NOT NULL,
            status TEXT NOT NULL,
            last_fired_revision INTEGER NOT NULL DEFAULT -1,
            updated_at INTEGER NOT NULL,
            paused INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE events(id TEXT PRIMARY KEY,reminder_id TEXT,kind TEXT,revision INTEGER,at_ms INTEGER);
        CREATE TABLE schedule_jobs(
            id TEXT PRIMARY KEY,reminder_id TEXT,kind TEXT,revision INTEGER,fire_at INTEGER,
            state TEXT,attempts INTEGER,error TEXT,created_at INTEGER
        );
        PRAGMA user_version=2;
        INSERT INTO reminders VALUES('r1','Old single',1234,1234,4,'DONE',-1,1200,0,0);
        INSERT INTO events VALUES('e1','r1','DONE',4,1300);
        INSERT INTO schedule_jobs VALUES('j1','r1','CANCEL',4,NULL,'APPLIED',1,NULL,1300);
        """)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def migrate(self):
        ddl=re.search(
            r'private fun upgradeRecurrenceTables\(db: SQLiteDatabase\)\s*\{(.*?)\n    \}',
            SRC,re.S)
        self.assertIsNotNone(ddl,"SQLite migration method missing")
        sql=re.findall(r'db\.execSQL\((?:"""(.*?)"""|"([^"]+)")\)',ddl.group(1),re.S)
        self.assertEqual(4,len(sql))
        for quoted,plain in sql:
            self.db.execute(quoted if quoted else plain)
        self.db.execute("PRAGMA user_version=3")
        self.db.commit()

    def test_old_data_preserved_and_nullable_series(self):
        before=self.db.execute("SELECT * FROM reminders").fetchone()
        self.migrate()
        after=self.db.execute("SELECT * FROM reminders").fetchone()
        self.assertEqual(before,after[:len(before)])
        self.assertEqual((None,None),after[-2:])
        self.assertEqual((3,),self.db.execute("PRAGMA user_version").fetchone())
        self.assertEqual(("e1","r1","DONE",4,1300),self.db.execute("SELECT * FROM events").fetchone())

    def test_recurring_slot_identity_is_unique(self):
        self.migrate()
        self.db.execute("""INSERT INTO recurrence_rules
            (id,title,anchor_ms,mode,days_mask,created_at,updated_at)
            VALUES('s1','Walk',2000,'DAILY',0,100,100)""")
        def add(i):
            self.db.execute("""INSERT INTO reminders(id,title,due_at,original_due_at,
                revision,status,updated_at,series_id,slot_key)
                VALUES(?,'Walk',2000,2000,1,'PENDING',100,'s1','2026-10-23')""",(i,))
        add("o1")
        with self.assertRaises(sqlite3.IntegrityError):
            add("o2")
        self.db.execute("INSERT INTO reminders(id,title,due_at,original_due_at,revision,status,updated_at) VALUES('single2','Once',2000,2000,1,'PENDING',100)")
        self.assertEqual(3,self.db.execute("SELECT COUNT(*) FROM reminders").fetchone()[0])

    def test_invalid_recurrence_mode_rejected(self):
        self.migrate()
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("""INSERT INTO recurrence_rules(id,title,anchor_ms,mode,days_mask,created_at,updated_at)
                VALUES('s1','Bad',2000,'NEVER',0,100,100)""")

if __name__=="__main__":
    unittest.main()
