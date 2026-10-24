import SwiftUI

/// R3b reminder detail is an evidence view. It reads the original audit
/// records and does not reclassify an unanswered alert as a skipped task.
struct QuietPrecisionReminderDetail: View {
    let reminder: IOSReminder
    @ObservedObject private var store = IOSReminderStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var confirmArchive = false

    private var current: IOSReminder {
        store.reminders.first(where: { $0.id == reminder.id }) ?? reminder
    }
    private var activity: [IOSReminderEvent] {
        store.history(for: reminder.id)
    }
    var body: some View {
        NavigationStack {
            List {
                Section("Reminder") {
                    Text(current.title).font(.headline)
                    LabeledContent("Due") {
                        Text(current.dueAt, format: .dateTime.day().month().year().hour().minute())
                    }
                    LabeledContent("Outcome") {
                        Text(current.outcome == .done ? "Done" :
                             current.outcome == .skipped ? "Skipped" :
                             "Unresolved (no recorded completion)")
                    }
                    if current.seriesID != nil {
                        LabeledContent("Repeat slot", value: current.slotKey ?? "")
                    }
                }
                Section("Recorded actions") {
                    if activity.isEmpty {
                        Text("No actions recorded.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(activity) { event in
                            VStack(alignment: .leading, spacing: 5) {
                                Text(event.kind)
                                    .font(.subheadline.weight(.medium))
                                Text(event.recordedAt, format: .dateTime.day().month().year().hour().minute())
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 3)
                        }
                    }
                }
                if !current.archived && current.seriesID == nil {
                    Section {
                        Button("Archive reminder", role: .destructive) {
                            confirmArchive = true
                        }
                    } footer: {
                        Text("Archived reminders keep their history. Future notifications for this reminder are cancelled.")
                    }
                }
            }
            .navigationTitle("Reminder history")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close") { dismiss() }
                }
            }
            .confirmationDialog("Archive reminder?",
                isPresented: $confirmArchive, titleVisibility: .visible) {
                Button("Archive", role: .destructive) {
                    Task { @MainActor in
                        await store.archive(id: current.id)
                        if store.lastError == nil { dismiss() }
                    }
                }
                Button("Keep", role: .cancel) {}
            } message: {
                Text("This hides the reminder and cancels its future notification, without erasing your history.")
            }
        }
    }
}
