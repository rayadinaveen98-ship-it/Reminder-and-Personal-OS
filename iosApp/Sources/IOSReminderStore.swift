import Foundation
import UserNotifications
import Combine

/// A native iOS persistence *alpha*, not the KMP production database.
/// Unknown/no response is not Done or Skipped. Records survive normal app restarts.
struct IOSReminder: Codable, Identifiable, Equatable {
    enum Outcome: String, Codable { case unknown = "UNKNOWN", done = "DONE", skipped = "SKIPPED" }
    var id: UUID
    var title: String
    var dueAt: Date
    var originalDueAt: Date
    var revision: Int
    var outcome: Outcome
    var archived: Bool
}

struct IOSReminderEvent: Codable, Identifiable {
    var id: UUID
    var reminderID: UUID
    var kind: String
    var revision: Int
    var recordedAt: Date
}

struct IOSReminderSnapshot: Codable {
    var version: Int
    var reminders: [IOSReminder]
    var events: [IOSReminderEvent]
}

@MainActor
final class IOSReminderStore: ObservableObject {
    static let shared = IOSReminderStore()

    @Published private(set) var reminders: [IOSReminder] = []
    @Published private(set) var events: [IOSReminderEvent] = []
    @Published private(set) var permissionStatus = "Checking notification permission…"
    @Published private(set) var lastError: String?

    private let center = UNUserNotificationCenter.current()
    private let filename = "ios-reminders-alpha-v1.json"
    private let notificationPrefix = "reminderos.ios.alpha."
    private let storageOverride: URL?

    private var fileURL: URL {
        if let storageOverride { return storageOverride }
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return directory.appendingPathComponent(filename)
    }

    /// Optional private file location enables isolated, non-destructive XCTest.
    /// The normal app always uses Application Support.
    init(storageURL: URL? = nil) {
        self.storageOverride = storageURL
        do {
            let url = fileURL
            if FileManager.default.fileExists(atPath: url.path) {
                let saved = try JSONDecoder().decode(IOSReminderSnapshot.self, from: Data(contentsOf: url))
                guard saved.version == 1 else { throw CocoaError(.fileReadCorruptFile) }
                reminders = saved.reminders
                events = saved.events
            }
        } catch {
            // Do not overwrite a corrupt/unsupported store on the next mutation.
            lastError = "Saved reminders could not be read: \(error.localizedDescription). Existing file preserved."
            readOnlyBecauseLoadFailed = true
        }
    }

    private var readOnlyBecauseLoadFailed = false

    func clearError() { lastError = nil }

    private func save(_ newReminders: [IOSReminder], _ newEvents: [IOSReminderEvent]) throws {
        guard !readOnlyBecauseLoadFailed else {
            throw NSError(domain: "ReminderOS", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "Stored data needs recovery before editing"])
        }
        let url = fileURL
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(), withIntermediateDirectories: true
        )
        let data = try JSONEncoder().encode(
            IOSReminderSnapshot(version: 1, reminders: newReminders, events: newEvents)
        )
        // Atomically replace snapshot so a crash doesn't truncate all reminders.
        try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        reminders = newReminders
        events = newEvents
    }

    private func identifier(_ id: UUID) -> String { notificationPrefix + id.uuidString }

    private func appendEvent(
        _ kind: String, for reminder: IOSReminder,
        list: inout [IOSReminderEvent]
    ) {
        list.append(IOSReminderEvent(
            id: UUID(), reminderID: reminder.id, kind: kind,
            revision: reminder.revision, recordedAt: .now
        ))
    }

    func refreshAuthorization() async {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized: permissionStatus = "Notifications authorized"
        case .provisional: permissionStatus = "Notifications provisional (may be quiet)"
        case .ephemeral: permissionStatus = "Temporary notification permission"
        case .denied: permissionStatus = "Notifications denied: change in Settings"
        case .notDetermined: permissionStatus = "Notification permission not requested"
        @unknown default: permissionStatus = "Notification permission unknown"
        }
    }

    func requestPermission() async {
        do {
            _ = try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            lastError = "Permission request failed: \(error.localizedDescription)"
        }
        await refreshAuthorization()
        await reconcile()
    }

    /// Save intent first. Scheduling the OS notification is a separate, retryable step.
    func create(title: String, dueAt: Date) async -> Bool {
        let text = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty && text.count <= 200, dueAt > Date().addingTimeInterval(2) else {
            lastError = "Enter a title and a future time."
            return false
        }
        let row = IOSReminder(
            id: UUID(), title: text, dueAt: dueAt, originalDueAt: dueAt,
            revision: 1, outcome: .unknown, archived: false
        )
        do {
            var updated = reminders
            var log = events
            updated.append(row)
            appendEvent("CREATED", for: row, list: &log)
            try save(updated, log)
            await reconcile()
            return true
        } catch {
            lastError = "Saving reminder failed: \(error.localizedDescription)"
            return false
        }
    }

    /// Stale notification action revisions and already resolved reminders are ignored.
    @discardableResult
    func respond(id: UUID, revision: Int, action: String) async -> Bool {
        guard let index = reminders.firstIndex(where: { $0.id == id }) else { return false }
        let old = reminders[index]
        guard old.revision == revision, old.outcome == .unknown, !old.archived else { return false }
        var next = old
        switch action {
        case "DONE": next.outcome = .done
        case "SKIP": next.outcome = .skipped
        case "SNOOZE":
            next.dueAt = .now.addingTimeInterval(10 * 60)
            next.revision += 1
        default: return false
        }
        do {
            var updated = reminders
            var log = events
            updated[index] = next
            appendEvent(action, for: next, list: &log)
            try save(updated, log)
            center.removePendingNotificationRequests(withIdentifiers: [identifier(id)])
            center.removeDeliveredNotifications(withIdentifiers: [identifier(id)])
            await reconcile()
            return true
        } catch {
            lastError = "Could not save action: \(error.localizedDescription)"
            return false
        }
    }

    func archive(id: UUID) async {
        guard let index = reminders.firstIndex(where: { $0.id == id }),
              !reminders[index].archived else { return }
        do {
            var updated = reminders
            var log = events
            updated[index].archived = true
            updated[index].revision += 1
            appendEvent("ARCHIVED", for: updated[index], list: &log)
            try save(updated, log)
            center.removePendingNotificationRequests(withIdentifiers: [identifier(id)])
            center.removeDeliveredNotifications(withIdentifiers: [identifier(id)])
        } catch { lastError = "Archiving failed: \(error.localizedDescription)" }
    }

    /// iOS limits pending notification requests. Intentionally cap this alpha at
    /// 48 pending requests so it does not silently ask the OS for an unbounded set.
    /// Already-due unresolved reminders stay UNKNOWN and are shown as overdue.
    func reconcile() async {
        await refreshAuthorization()
        let settings = await center.notificationSettings()
        guard [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus) else {
            lastError = "Allow notifications in Settings for scheduled alerts."
            return
        }
        let all = await center.pendingNotificationRequests()
        let existing = Dictionary(
            uniqueKeysWithValues: all.filter { $0.identifier.hasPrefix(notificationPrefix) }
                .map { ($0.identifier, $0) }
        )
        let now = Date()
        let eligible = reminders.filter {
            !$0.archived && $0.outcome == .unknown && $0.dueAt > now
        }.sorted { $0.dueAt < $1.dueAt }
        let selected = Array(eligible.prefix(48))
        let keep = Set(selected.map { identifier($0.id) })
        center.removePendingNotificationRequests(
            withIdentifiers: Array(existing.keys.filter { !keep.contains($0) })
        )
        for reminder in selected {
            let key = identifier(reminder.id)
            let due = reminder.dueAt
            if let request = existing[key],
               (request.content.userInfo["revision"] as? Int) == reminder.revision,
               let trigger = request.trigger as? UNCalendarNotificationTrigger,
               let next = trigger.nextTriggerDate(),
               abs(next.timeIntervalSince(due)) < 2 {
                continue
            }
            let content = UNMutableNotificationContent()
            content.title = reminder.title
            content.body = "Reminder due — choose Done, Snooze or Skip."
            content.sound = .default
            content.categoryIdentifier = "REMINDER_ACTIONS"
            content.userInfo = [
                "reminderID": reminder.id.uuidString, "revision": reminder.revision
            ]
            let fields: Set<Calendar.Component> = [
                .year, .month, .day, .hour, .minute, .second
            ]
            var components = Calendar.current.dateComponents(fields, from: due)
            components.timeZone = .current
            let request = UNNotificationRequest(
                identifier: key, content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            )
            do { try await center.add(request) }
            catch { lastError = "Notification request failed: \(error.localizedDescription)" }
        }
        if eligible.count > 48 {
            lastError = "iOS alpha schedules at most 48 upcoming reminders. Open the app to refill the queue."
        }
    }

    var visibleReminders: [IOSReminder] {
        reminders.filter { !$0.archived }.sorted {
            if $0.outcome == .unknown && $1.outcome != .unknown { return true }
            if $0.outcome != .unknown && $1.outcome == .unknown { return false }
            return $0.dueAt < $1.dueAt
        }
    }

    func history(for id: UUID) -> [IOSReminderEvent] {
        events.filter { $0.reminderID == id }
            .sorted { $0.recordedAt > $1.recordedAt }
    }
}
