import SwiftUI
import UserNotifications

/// iOS native reminder alpha. A Kotlin Multiplatform database/logic bridge
/// is future work; current persistent reminders are scoped to this iOS app.
@main
struct ReminderOSPreviewApp: App {
    @UIApplicationDelegateAdaptor(ReminderAppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup { QuietPrecisionHomeView() }
    }
}

final class ReminderAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private let center = UNUserNotificationCenter.current()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        center.delegate = self
        let done = UNNotificationAction(identifier: "DONE", title: "Done")
        let snooze = UNNotificationAction(identifier: "SNOOZE", title: "Snooze 10m")
        let skip = UNNotificationAction(identifier: "SKIP", title: "Skip", options: [.destructive])
        let category = UNNotificationCategory(
            identifier: "REMINDER_ACTIONS",
            actions: [done, snooze, skip], intentIdentifiers: [], options: []
        )
        center.setNotificationCategories([category])
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    /// Apple may launch the app in the background to deliver the selected
    /// action. Persist explicit response first; only then acknowledge callback.
    /// Opening or dismissing a notification does not mean Done.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let action = response.actionIdentifier
        let data = response.notification.request.content.userInfo
        guard ["DONE", "SNOOZE", "SKIP"].contains(action),
              let rawID = data["reminderID"] as? String,
              let id = UUID(uuidString: rawID),
              let revision = data["revision"] as? Int else {
            completionHandler()
            return
        }
        Task { @MainActor in
            _ = await IOSReminderStore.shared.respond(
                id: id, revision: revision, action: action
            )
            completionHandler()
        }
    }
}
