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
        XCTAssertTrue(await store.createRecurring(
            title: "Original", dueAt: future, pattern: .daily, weekdayMask: 0
        ))
        let old = try XCTUnwrap(store.activeRules.first)
        let occurrence = try XCTUnwrap(store.reminders.first { $0.seriesID == old.id })
        XCTAssertTrue(await store.respond(
            id: occurrence.id, revision: occurrence.revision, action: "DONE"
        ))
        XCTAssertTrue(await store.replaceRecurring(
            expected: old, title: "Revised", dueAt: future.addingTimeInterval(3600),
            pattern: .weekly, weekdayMask: 31
        ))
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
        XCTAssertTrue(await store.createRecurring(
            title: "Original", dueAt: future, pattern: .weekly, weekdayMask: 31
        ))
        let original = try XCTUnwrap(store.activeRules.first)
        let before = try Data(contentsOf: url)
        XCTAssertFalse(await store.replaceRecurring(
            expected: original, title: "", dueAt: future,
            pattern: .weekly, weekdayMask: 31
        ))
        XCTAssertEqual(try Data(contentsOf: url), before)
        await store.setRoutinePaused(id: original.id, paused: true)
        let pausedSnapshot = try Data(contentsOf: url)
        XCTAssertFalse(await store.replaceRecurring(
            expected: original, title: "Stale", dueAt: future,
            pattern: .daily, weekdayMask: 0
        ))
        XCTAssertEqual(try Data(contentsOf: url), pausedSnapshot)
    }
}
