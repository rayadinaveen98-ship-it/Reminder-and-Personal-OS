import SwiftUI
import XCTest
@testable import ReminderOSPreview

/// Hosting test only: this does not create reminders or prove device delivery.
final class QuietPrecisionEditorViewTests: XCTestCase {
    @MainActor
    func testNewReminderAndHistoryViewsHostWithoutAlteringStore() {
        let editor = UIHostingController(rootView: QuietPrecisionReminderEditor())
        editor.loadViewIfNeeded()
        XCTAssertNotNil(editor.view)
        let example = IOSReminder(
            id: UUID(), title: "Synthetic UI fixture",
            dueAt: Date().addingTimeInterval(3600),
            originalDueAt: Date().addingTimeInterval(3600),
            revision: 1, outcome: .unknown, archived: false
        )
        let detail = UIHostingController(
            rootView: QuietPrecisionReminderDetail(reminder: example)
        )
        detail.loadViewIfNeeded()
        XCTAssertNotNil(detail.view)
    }
}
