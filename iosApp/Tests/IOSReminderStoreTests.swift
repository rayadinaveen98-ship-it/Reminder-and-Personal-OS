import Foundation
import XCTest
@testable import ReminderOSPreview

final class IOSReminderStoreTests: XCTestCase {
    private func privateURL() throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("rpos-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: dir) }
        return dir.appendingPathComponent("snapshot.json")
    }

    @MainActor
    func testCreateThenReloadKeepsUnknownOutcome() async throws {
        let url = try privateURL()
        let store = IOSReminderStore(storageURL: url)
        let created = await store.create(
            title: "Drink water", dueAt: .now.addingTimeInterval(3600)
        )
        XCTAssertTrue(created)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        let restored = IOSReminderStore(storageURL: url)
        XCTAssertEqual(restored.reminders.count, 1)
        XCTAssertEqual(restored.reminders.first?.title, "Drink water")
        XCTAssertEqual(restored.reminders.first?.revision, 1)
        XCTAssertEqual(restored.reminders.first?.outcome, .unknown)
        XCTAssertEqual(restored.events.count, 1)
        XCTAssertEqual(restored.events.first?.kind, "CREATED")
    }

    @MainActor
    func testSnoozePreservesHistoryAndRejectsStaleDone() async throws {
        let url = try privateURL()
        let store = IOSReminderStore(storageURL: url)
        let created = await store.create(
            title: "Walk", dueAt: .now.addingTimeInterval(3600)
        )
        XCTAssertTrue(created)
        let first = try XCTUnwrap(store.reminders.first)
        let snoozed = await store.respond(
            id: first.id, revision: first.revision, action: "SNOOZE"
        )
        XCTAssertTrue(snoozed)
        let staleDone = await store.respond(
            id: first.id, revision: first.revision, action: "DONE"
        )
        XCTAssertFalse(staleDone)
        let restored = IOSReminderStore(storageURL: url)
        let record = try XCTUnwrap(restored.reminders.first)
        XCTAssertEqual(record.outcome, .unknown)
        XCTAssertEqual(record.revision, 2)
        XCTAssertEqual(restored.events.map(\.kind), ["CREATED", "SNOOZE"])
    }

    @MainActor
    func testDonePersistsAndSecondActionIsIgnored() async throws {
        let url = try privateURL()
        let store = IOSReminderStore(storageURL: url)
        let created = await store.create(
            title: "Study", dueAt: .now.addingTimeInterval(3600)
        )
        XCTAssertTrue(created)
        let first = try XCTUnwrap(store.reminders.first)
        let didComplete = await store.respond(id: first.id, revision: 1, action: "DONE")
        let staleSkip = await store.respond(id: first.id, revision: 1, action: "SKIP")
        XCTAssertTrue(didComplete)
        XCTAssertFalse(staleSkip)
        let restored = IOSReminderStore(storageURL: url)
        XCTAssertEqual(restored.reminders.first?.outcome, .done)
        XCTAssertEqual(restored.events.map(\.kind), ["CREATED", "DONE"])
    }

    @MainActor
    func testArchiveRetainsRecordAndAuditEvent() async throws {
        let url = try privateURL()
        let store = IOSReminderStore(storageURL: url)
        let created = await store.create(
            title: "Water plants", dueAt: .now.addingTimeInterval(3600)
        )
        XCTAssertTrue(created)
        let first = try XCTUnwrap(store.reminders.first)
        await store.archive(id: first.id)
        let reloaded = IOSReminderStore(storageURL: url)
        XCTAssertEqual(reloaded.reminders.count, 1)
        XCTAssertEqual(reloaded.visibleReminders.count, 0)
        XCTAssertTrue(reloaded.reminders[0].archived)
        XCTAssertEqual(reloaded.events.last?.kind, "ARCHIVED")
    }

    @MainActor
    func testCorruptedStoreCannotBeOverwritten() async throws {
        let url = try privateURL()
        let corrupted = Data("{invalid".utf8)
        try corrupted.write(to: url, options: .atomic)
        let store = IOSReminderStore(storageURL: url)
        XCTAssertNotNil(store.lastError)
        let created = await store.create(
            title: "Must fail", dueAt: .now.addingTimeInterval(3600)
        )
        XCTAssertFalse(created)
        XCTAssertEqual(try Data(contentsOf: url), corrupted)
    }
}
