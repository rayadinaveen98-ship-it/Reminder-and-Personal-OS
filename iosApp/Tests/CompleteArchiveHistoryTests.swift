import Foundation
import XCTest
@testable import ReminderOSPreview

final class CompleteArchiveHistoryTests: XCTestCase {
    @MainActor
    private func makeStore() throws -> (IOSReminderStore, URL) {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("r3e-history-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("local-store.json")
        return (IOSReminderStore(storageURL: url), url)
    }

    @MainActor
    func testArchiveFindsOneOffAndOldRecurringSlotsWithoutMutatingSnapshot() async throws {
        let (store, file) = try makeStore()
        let future = Date.now.addingTimeInterval(86400)
        let made = await store.create(title: "Walk archived", dueAt: future)
        XCTAssertTrue(made)
        let once = try XCTUnwrap(store.reminders.first(where: { $0.seriesID == nil }))
        await store.archive(id: once.id)
        let recurring = await store.createRecurring(title: "Read at night",
            dueAt: future, pattern: .daily, weekdayMask: 0)
        XCTAssertTrue(recurring)
        let rule = try XCTUnwrap(store.activeRules.first)
        let count = store.reminders.filter { $0.seriesID == rule.id }.count
        XCTAssertGreaterThan(count, 2)
        await store.archiveRoutine(id: rule.id)

        let before = try Data(contentsOf: file)
        let archive = store.historyRecords(archiveOnly: true)
        XCTAssertTrue(archive.contains(where: { $0.id == once.id && $0.archived }))
        XCTAssertEqual(archive.filter { $0.seriesID == rule.id }.count, count)
        XCTAssertTrue(archive.contains(where: {
            $0.seriesID == rule.id && !$0.archived
        }))
        XCTAssertEqual(store.historyRecords(archiveOnly: true,
            query: "read AT").filter { $0.seriesID == rule.id }.count, count)
        XCTAssertTrue(store.historyRecords(archiveOnly: true,
            outcome: .done).isEmpty)
        XCTAssertEqual(try Data(contentsOf: file), before)
    }

    @MainActor
    func testHistoryIncludesResolvedAndOverdueButNotFutureUnknown() async throws {
        let (store, file) = try makeStore()
        let future = Date.now.addingTimeInterval(86400)
        let created = await store.create(title: "Future task", dueAt: future)
        XCTAssertTrue(created)
        let row = try XCTUnwrap(store.reminders.first)
        let accepted = await store.respond(id: row.id, revision: row.revision, action: "DONE")
        XCTAssertTrue(accepted)
        let another = await store.create(title: "Still future", dueAt: future.addingTimeInterval(60))
        XCTAssertTrue(another)

        let bytes = try Data(contentsOf: file)
        let history = store.historyRecords(archiveOnly: false)
        XCTAssertTrue(history.contains(where: { $0.id == row.id && $0.outcome == .done }))
        XCTAssertFalse(history.contains(where: { $0.title == "Still future" }))
        XCTAssertEqual(store.historyRecords(archiveOnly: false,
            outcome: .done).count, 1)
        XCTAssertEqual(try Data(contentsOf: file), bytes)
    }
}
