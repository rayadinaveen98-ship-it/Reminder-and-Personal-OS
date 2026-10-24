import SwiftUI
import XCTest
@testable import ReminderOSPreview

/// Hosting smoke only. Mutating recurring-rule behaviors are covered
/// separately by IOSReminderStoreTests; native delivery needs iPhone QA.
final class QuietPrecisionManagementViewTests: XCTestCase {
    @MainActor
    func testManagementAndSettingsHostUsingExistingStore() {
        let host = UIHostingController(rootView: QuietPrecisionManagementView())
        host.loadViewIfNeeded()
        XCTAssertNotNil(host.view)
        XCTAssertNotNil(IOSReminderStore.shared.permissionStatus)
    }
}
