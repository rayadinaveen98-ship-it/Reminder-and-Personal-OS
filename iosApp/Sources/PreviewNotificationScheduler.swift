import Foundation
import UserNotifications

/// One pending test notification using Apple's native scheduler.
/// Requests persist in the OS outside the app process; no background timer is used.
@MainActor
struct PreviewNotificationScheduler {
    static let identifier = "dev.reminderos.preview.one"

    func permissionSummary() async -> String {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .authorized: return "Authorized"
        case .provisional: return "Provisional / may be delivered quietly"
        case .ephemeral: return "Temporary authorization"
        case .denied: return "Denied — change permissions in iOS Settings"
        case .notDetermined: return "Not requested"
        @unknown default: return "Unknown authorization state"
        }
    }

    func requestPermission() async -> String {
        do {
            let granted = try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])
            return granted ? "Permission granted" : "Permission denied"
        } catch {
            return "Permission request failed: \(error.localizedDescription)"
        }
    }

    func scheduleTest() async -> String {
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings()
        switch status.authorizationStatus {
        case .authorized, .provisional, .ephemeral: break
        default: return "Please enable notifications before scheduling"
        }
        let content = UNMutableNotificationContent()
        content.title = "Reminder preview"
        content.body = "Your scheduled test reminder is due."
        content.sound = .default
        // A bounded one-time request, not repeating or an app-owned background timer.
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 60, repeats: false)
        let request = UNNotificationRequest(
            identifier: Self.identifier, content: content, trigger: trigger
        )
        do {
            try await center.add(request)
            return "A native iOS reminder was requested for about 60 seconds from now."
        } catch {
            return "Scheduling failed: \(error.localizedDescription)"
        }
    }

    func cancelTest() async -> String {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
        // Removal API is asynchronous; this is a request, not delivery verification.
        return "Cancellation requested for the pending test alert"
    }

    func pendingSummary() async -> String {
        let requests = await UNUserNotificationCenter.current().pendingNotificationRequests()
        return requests.contains(where: { $0.identifier == Self.identifier })
            ? "One test notification is pending"
            : "No test notification is pending"
    }
}
