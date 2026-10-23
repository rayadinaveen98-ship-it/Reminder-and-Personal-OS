import Foundation
import XCTest
@testable import ReminderOSPreview

final class IOSPortableStorageInspectionTests: XCTestCase {
    private func privateURL() throws -> URL {
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(
            "portable-\(UUID().uuidString)",isDirectory: true
        )
        try FileManager.default.createDirectory(at:dir,withIntermediateDirectories:true)
        addTeardownBlock { try? FileManager.default.removeItem(at:dir) }
        return dir.appendingPathComponent("snapshot.json")
    }

    @MainActor
    func testInspectionIncludesRecurringUnknownAndAuditWithoutWriting() async throws {
        let url=try privateURL()
        let store=IOSReminderStore(storageURL:url)
        let created=await store.createRecurring(
            title:"Hydrate",dueAt:Date().addingTimeInterval(3600),
            pattern:.daily,weekdayMask:31 // Old UI set mask even on daily.
        )
        XCTAssertTrue(created)
        let before=try Data(contentsOf:url)
        let summary=try store.inspectMigrationReadiness()
        let after=try Data(contentsOf:url)
        XCTAssertEqual(before,after)
        XCTAssertTrue(summary.contains("Recurring schedules: 1"))
        XCTAssertTrue(summary.contains("Reminder occurrences: 7"))
        XCTAssertTrue(summary.contains("Unanswered occurrences: 7"))
    }

    @MainActor
    func testInspectionRejectsDuplicateLogicalSlotWithoutChangingSnapshot() throws {
        let url=try privateURL()
        let series=UUID()
        let now=Date().addingTimeInterval(3600)
        let rule=IOSRecurrenceRule(
            id:series,title:"Routine",anchorAt:now,
            pattern:.daily,weekdayMask:0,paused:false,archived:false
        )
        let a=IOSReminder(
            id:UUID(),title:"Routine",dueAt:now,originalDueAt:now,
            revision:1,outcome:.unknown,archived:false,seriesID:series,slotKey:"2026-10-23"
        )
        let b=IOSReminder(
            id:UUID(),title:"Routine",dueAt:now,originalDueAt:now,
            revision:1,outcome:.unknown,archived:false,seriesID:series,slotKey:"2026-10-23"
        )
        try JSONEncoder().encode(IOSReminderSnapshot(
            version:2,reminders:[a,b],events:[],recurrenceRules:[rule]
        )).write(to:url,options:.atomic)
        let before=try Data(contentsOf:url)
        let store=IOSReminderStore(storageURL:url)
        XCTAssertThrowsError(try store.inspectMigrationReadiness())
        XCTAssertEqual(try Data(contentsOf:url),before)
    }

    @MainActor
    func testUnsupportedStoredFileStillProtectedDuringInspection() throws {
        let url=try privateURL()
        let invalid=Data("{corrupt".utf8)
        try invalid.write(to:url)
        let store=IOSReminderStore(storageURL:url)
        XCTAssertThrowsError(try store.inspectMigrationReadiness())
        XCTAssertEqual(try Data(contentsOf:url),invalid)
    }
}
