import SwiftUI

/// iOS V0.4a: native persistent one-time reminders, not yet full KMP parity.
struct PreviewContentView: View {
    @ObservedObject private var store = IOSReminderStore.shared
    @State private var title = ""
    @State private var dueAt = Date().addingTimeInterval(5 * 60)
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Reminder & Personal OS")
                        .font(.title2.bold())
                    Text("iOS alpha · One-time reminders saved on this device")
                        .foregroundStyle(.secondary)
                    Text(store.permissionStatus)
                        .font(.footnote)
                    Button("Request notification permission") {
                        Task { await store.requestPermission() }
                    }
                }
                Section("New reminder") {
                    TextField("What do you need to remember?", text: $title)
                        .textInputAutocapitalization(.sentences)
                        .accessibilityIdentifier("reminderTitle")
                    DatePicker("Due", selection: $dueAt, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                    Button("Save reminder") {
                        Task {
                            busy = true
                            let saved = await store.create(title: title, dueAt: dueAt)
                            if saved {
                                title = ""
                                dueAt = Date().addingTimeInterval(5 * 60)
                            }
                            busy = false
                        }
                    }
                    .disabled(busy || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier("saveReminder")
                }
                Section("Your reminders") {
                    if store.visibleReminders.isEmpty {
                        Text("No reminders yet. Add your first one above.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(store.visibleReminders) { reminder in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(reminder.title)
                                .font(.headline)
                            Text(reminder.dueAt, style: .date)
                                .font(.subheadline)
                            Text(reminder.dueAt, style: .time)
                                .font(.subheadline)
                            HStack {
                                if reminder.outcome == .done {
                                    Label("Done", systemImage: "checkmark.circle.fill")
                                } else if reminder.outcome == .skipped {
                                    Label("Skipped", systemImage: "forward.fill")
                                } else if reminder.dueAt < Date() {
                                    Label("Overdue · unresolved", systemImage: "clock")
                                } else {
                                    Label("Pending", systemImage: "clock")
                                }
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            if reminder.outcome == .unknown {
                                HStack {
                                    Button("Done") { respond(reminder, "DONE") }
                                    Button("Snooze 10m") { respond(reminder, "SNOOZE") }
                                    Button("Skip") { respond(reminder, "SKIP") }
                                }
                                .font(.caption)
                                .buttonStyle(.bordered)
                            }
                            NavigationLink("History") {
                                List(store.history(for: reminder.id)) { event in
                                    VStack(alignment: .leading) {
                                        Text(event.kind).font(.headline)
                                        Text(event.recordedAt, style: .date)
                                        Text(event.recordedAt, style: .time)
                                    }
                                }
                                .navigationTitle("Reminder history")
                            }
                            .font(.caption)
                            Button("Archive", role: .destructive) {
                                Task { await store.archive(id: reminder.id) }
                            }
                            .font(.caption)
                        }
                        .padding(.vertical, 5)
                    }
                }
                Section {
                    Button("Check & repair future alerts") {
                        Task { await store.reconcile() }
                    }
                    Text("Unanswered reminders stay unresolved. iOS notification timing, Focus settings and delivery are controlled by the operating system.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Reminder OS")
            .task {
                await store.refreshAuthorization()
                await store.reconcile()
            }
            .refreshable { await store.reconcile() }
            .alert("Reminder status", isPresented: Binding(
                get: { store.lastError != nil },
                set: { if !$0 { store.clearError() } }
            )) {
                Button("OK") { store.clearError() }
            } message: {
                Text(store.lastError ?? "")
            }
        }
    }

    private func respond(_ reminder: IOSReminder, _ action: String) {
        Task {
            _ = await store.respond(id: reminder.id, revision: reminder.revision, action: action)
        }
    }
}
