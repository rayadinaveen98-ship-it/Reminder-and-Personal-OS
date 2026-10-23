import Foundation
import XCTest
@testable import ReminderOSPreview

final class IOSCalendarRecurrenceTests: XCTestCase {
    private var utc: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        return cal
    }

    private func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 8,
                      _ min: Int = 30, calendar: Calendar) -> Date {
        calendar.date(from: DateComponents(
            year: y, month: m, day: d, hour: h, minute: min
        ))!
    }

    private func rule(_ anchor: Date, _ mode: IOSRepeatPattern,
                      mask: Int = 0) -> IOSRecurrenceRule {
        IOSRecurrenceRule(id: UUID(), title: "Routine", anchorAt: anchor,
                          pattern: mode, weekdayMask: mask,
                          paused: false, archived: false)
    }

    func testDailyKeepsWallClockHourAcrossDays() throws {
        let start = date(2026, 10, 23, calendar: utc)
        let r = rule(start, .daily)
        let next = try XCTUnwrap(IOSCalendarRecurrence.next(rule: r, after: start, calendar: utc))
        XCTAssertEqual(IOSCalendarRecurrence.slotKey(next, calendar: utc), "2026-10-24")
        XCTAssertEqual(utc.component(.hour, from: next), 8)
    }

    func testSelectedWeekdaysSkipWeekend() throws {
        let friday = date(2026, 10, 23, calendar: utc)
        let r = rule(friday, .weekly, mask: 31)
        let monday = try XCTUnwrap(IOSCalendarRecurrence.next(rule: r, after: friday, calendar: utc))
        XCTAssertEqual(IOSCalendarRecurrence.slotKey(monday, calendar: utc), "2026-10-26")
    }

    func testWeeklyRejectsEmptyMask() {
        let start = date(2026, 10, 23, calendar: utc)
        XCTAssertNil(IOSCalendarRecurrence.next(rule: rule(start, .weekly),
                                                 after: start, calendar: utc))
    }

    func testMissingMonthlyDateSkipped() throws {
        let jan31 = date(2026, 1, 31, calendar: utc)
        let next = try XCTUnwrap(IOSCalendarRecurrence.next(
            rule: rule(jan31, .monthly), after: jan31, calendar: utc
        ))
        XCTAssertEqual(IOSCalendarRecurrence.slotKey(next, calendar: utc), "2026-03-31")
    }

    func testLeapDayExistsWhenAppropriate() throws {
        let jan29 = date(2024, 1, 29, calendar: utc)
        let feb = try XCTUnwrap(IOSCalendarRecurrence.next(
            rule: rule(jan29, .monthly), after: jan29, calendar: utc
        ))
        XCTAssertEqual(IOSCalendarRecurrence.slotKey(feb, calendar: utc), "2024-02-29")
    }

    func testSpringDSTPreservesWallClock() throws {
        var ny = Calendar(identifier: .gregorian)
        ny.timeZone = TimeZone(identifier: "America/New_York")!
        let march7 = date(2026, 3, 7, calendar: ny)
        let march8 = try XCTUnwrap(IOSCalendarRecurrence.next(
            rule: rule(march7, .daily), after: march7, calendar: ny
        ))
        XCTAssertEqual(ny.component(.hour, from: march8), 8)
        XCTAssertEqual(march8.timeIntervalSince(march7), 23 * 3600, accuracy: 0.5)
    }

    func testFallDSTPreservesWallClock() throws {
        var ny = Calendar(identifier: .gregorian)
        ny.timeZone = TimeZone(identifier: "America/New_York")!
        let october31 = date(2026, 10, 31, calendar: ny)
        let nov1 = try XCTUnwrap(IOSCalendarRecurrence.next(
            rule: rule(october31, .daily), after: october31, calendar: ny
        ))
        XCTAssertEqual(ny.component(.hour, from: nov1), 8)
        XCTAssertEqual(nov1.timeIntervalSince(october31), 25 * 3600, accuracy: 0.5)
    }

    private func tempStorage() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(
            UUID().uuidString, isDirectory: true
        )
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        return folder.appendingPathComponent("store.json")
    }

    @MainActor
    func testDailyMaterializedIndependentOfEarlierResponse() async throws {
        let url = try tempStorage()
        let store = IOSReminderStore(storageURL: url)
        let created = await store.createRecurring(
            title: "Hydrate", dueAt: .now.addingTimeInterval(3600),
            pattern: .daily, weekdayMask: 0
        )
        XCTAssertTrue(created)
        let ruleID = try XCTUnwrap(store.recurrenceRules.first?.id)
        let slots = store.reminders.filter { $0.seriesID == ruleID }
        XCTAssertEqual(slots.count, 7)
        XCTAssertEqual(Set(slots.compactMap(\.slotKey)).count, 7)
        let first = try XCTUnwrap(slots.min { $0.originalDueAt < $1.originalDueAt })
        let done = await store.respond(id: first.id, revision: 1, action: "DONE")
        XCTAssertTrue(done)
        let reloaded = IOSReminderStore(storageURL: url)
        XCTAssertEqual(reloaded.recurrenceRules.count, 1)
        XCTAssertEqual(reloaded.reminders.filter { $0.seriesID == ruleID }.count, 7)
        XCTAssertEqual(reloaded.reminders.first(where: { $0.id == first.id })?.outcome, .done)
        XCTAssertEqual(reloaded.reminders.filter {
            $0.seriesID == ruleID && $0.outcome == .unknown
        }.count, 6)
    }

    @MainActor
    func testRoutinePausePersistsWithoutChangingOutcomes() async throws {
        let url = try tempStorage()
        let store = IOSReminderStore(storageURL: url)
        let created = await store.createRecurring(
            title: "Walk", dueAt: .now.addingTimeInterval(3600),
            pattern: .weekly, weekdayMask: 31
        )
        XCTAssertTrue(created)
        let id = try XCTUnwrap(store.recurrenceRules.first?.id)
        await store.setRoutinePaused(id: id, paused: true)
        let reloaded = IOSReminderStore(storageURL: url)
        XCTAssertTrue(reloaded.recurrenceRules.first?.paused ?? false)
        XCTAssertEqual(reloaded.reminders.filter { $0.outcome == .unknown }.count, 7)
    }

    @MainActor
    func testVersionOneSnapshotIsReadable() async throws {
        let url = try tempStorage()
        let original = IOSReminder(
            id: UUID(), title: "Legacy", dueAt: .now.addingTimeInterval(3600),
            originalDueAt: .now.addingTimeInterval(3600),
            revision: 1, outcome: .unknown, archived: false
        )
        let encoded = try JSONEncoder().encode(IOSReminderSnapshot(
            version: 1, reminders: [original], events: []
        ))
        // Omit the V2 recurrence field and occurrence keys to simulate the old file.
        var obj = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        obj.removeValue(forKey: "recurrenceRules")
        if var list = obj["reminders"] as? [[String: Any]], !list.isEmpty {
            list[0].removeValue(forKey: "seriesID")
            list[0].removeValue(forKey: "slotKey")
            obj["reminders"] = list
        }
        try JSONSerialization.data(withJSONObject: obj).write(to: url)
        let store = IOSReminderStore(storageURL: url)
        XCTAssertNil(store.lastError)
        XCTAssertEqual(store.reminders.first?.title, "Legacy")
        XCTAssertTrue(store.recurrenceRules.isEmpty)
        XCTAssertNil(store.reminders.first?.seriesID)
    }
}
