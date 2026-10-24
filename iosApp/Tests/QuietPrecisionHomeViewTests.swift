import SwiftUI
import XCTest
@testable import ReminderOSPreview

/// UI composition smoke only; this does not prove notification delivery or real-device fidelity.
final class QuietPrecisionHomeViewTests: XCTestCase {
    @MainActor
    func testNativeHomeShellCanBeHostedWithoutBackupImport() {
        let controller = UIHostingController(rootView: QuietPrecisionHomeView())
        controller.loadViewIfNeeded()
        XCTAssertNotNil(controller.view)
        // Existing reminder database and scheduling behavior remain covered by
        // IOSReminderStoreTests and real-device release QA is still required.
    }
}
