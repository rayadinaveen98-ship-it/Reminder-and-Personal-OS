import Foundation
import XCTest
@testable import ReminderOSPreview

final class SafeScheduleReplaceTests: XCTestCase {
    private func temporaryStore() throws -> (IOSReminderStore, URL) {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("r3d-replace-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("reminders.json")
        return (IOSReminderStore(storageURL: url), url)
    }

    @MainActor
    func testReplacePreservesDoneHistoryAndCancelsOldVisibleSlots() async throws {
        let (store, url) = try temporaryStore()
        let future = Date.now.addingTimeInterval(172800)
        let created = await store.createRecurring(
            title: "Original", dueAt: future, pattern: .daily, weekdayMask: 0
        )
        XCTAssertTrue(created)
        let old = try XCTUnwrap(store.activeRules.first)
        let occurrence = try XCTUnwrap(store.reminders.first { $0.seriesID == old.id })
        let completed = await store.respond(
            id: occurrence.id, revision: occurrence.revision, action: "DONE"
        )
        XCTAssertTrue(completed)
        let replaced = await store.replaceRecurring(
            expected: old, title: "Revised", dueAt: future.addingTimeInterval(3600),
            pattern: .weekly, weekdayMask: 31
        )
        XCTAssertTrue(replaced)
        XCTAssertEqual(store.activeRules.count, 1)
        XCTAssertEqual(store.archivedRules.count, 1)
        XCTAssertNotEqual(store.activeRules.first?.id, old.id)
        let retained = try XCTUnwrap(store.reminders.first { $0.id == occurrence.id })
        XCTAssertEqual(retained.outcome, .done)
        XCTAssertTrue(retained.archived)
        XCTAssertTrue(store.history(for: occurrence.id).contains { $0.kind == "DONE" })
        XCTAssertTrue(store.history(for: occurrence.id).contains { $0.kind == "RULE_REPLACED" })
        XCTAssertTrue(store.visibleReminders.contains { $0.seriesID == store.activeRules.first?.id })
        let reloaded = IOSReminderStore(storageURL: url)
        XCTAssertEqual(reloaded.archivedRules.count, 1)
        XCTAssertEqual(reloaded.activeRules.count, 1)
        XCTAssertEqual(reloaded.reminders.first { $0.id == occurrence.id }?.outcome, .done)
    }

    @MainActor
    func testInvalidOrStaleReplacementLeavesSnapshotUnchanged() async throws {
        let (store, url) = try temporaryStore()
        let future = Date.now.addingTimeInterval(172800)
        let created = await store.createRecurring(
            title: "Original", dueAt: future, pattern: .weekly, weekdayMask: 31
        )
        XCTAssertTrue(created)
        let original = try XCTUnwrap(store.activeRules.first)
        let before = try Data(contentsOf: url)
        let invalid = await store.replaceRecurring(
            expected: original, title: "", dueAt: future,
            pattern: .weekly, weekdayMask: 31
        )
        XCTAssertFalse(invalid)
        XCTAssertEqual(try Data(contentsOf: url), before)
        await store.setRoutinePaused(id: original.id, paused: true)
        let pausedSnapshot = try Data(contentsOf: url)
        let stale = await store.replaceRecurring(
            expected: original, title: "Stale", dueAt: future,
            pattern: .daily, weekdayMask: 0
        )
        XCTAssertFalse(stale)
        XCTAssertEqual(try Data(contentsOf: url), pausedSnapshot)
    }
}
