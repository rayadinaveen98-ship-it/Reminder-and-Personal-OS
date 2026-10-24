import SwiftUI
import UIKit

/// R3c: native management for recurring schedules, local reminder history,
/// notification permissions and access to existing encrypted backup tools.
/// No new store or background scheduling behavior is introduced.
struct QuietPrecisionManagementView: View {
    @ObservedObject private var store = IOSReminderStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var selection = 0
    @State private var ruleToArchive: IOSRecurrenceRule?
    @State private var reminderToArchive: IOSReminder?
    @State private var showingBackupTools = false
    @State private var selectedReminder: IOSReminder?
    @State private var showingEditor = false

    private let blue = Color(red: 0.239, green: 0.388, blue: 0.812)

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Category", selection: $selection) {
                        Text("Reminders").tag(0)
                        Text("Settings").tag(1)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("quietManagementSegment")
                }
                if selection == 0 {
                    schedulesSection
                    oneTimeSection
                    Section {
                        Button {
                            showingEditor = true
                        } label: {
                            Label("New reminder", systemImage: "plus.circle.fill")
                                .frame(minHeight: 44)
                        }
                    }
                } else {
                    diagnosticsSection
                    backupSection
                    privacySection
                }
            }
            .navigationTitle(selection == 0 ? "Manage reminders" : "Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .tint(blue)
        .sheet(isPresented: $showingBackupTools) {
            PreviewContentView()
        }
        .sheet(isPresented: $showingEditor) {
            QuietPrecisionReminderEditor()
        }
        .sheet(item: $selectedReminder) { reminder in
            QuietPrecisionReminderDetail(reminder: reminder)
        }
        .confirmationDialog(
            "Archive repeating schedule?",
            isPresented: Binding(
                get: { ruleToArchive != nil },
                set: { if !$0 { ruleToArchive = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Archive schedule", role: .destructive) {
                guard let rule = ruleToArchive else { return }
                ruleToArchive = nil
                Task { @MainActor in
                    await store.archiveRoutine(id: rule.id)
                }
            }
            Button("Keep schedule", role: .cancel) { ruleToArchive = nil }
        } message: {
            Text("Future notifications will stop. Past recorded outcomes stay in the local store. This alpha has no unarchive action.")
        }
        .confirmationDialog(
            "Archive one-time reminder?",
            isPresented: Binding(
                get: { reminderToArchive != nil },
                set: { if !$0 { reminderToArchive = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Archive reminder", role: .destructive) {
                guard let reminder = reminderToArchive else { return }
                reminderToArchive = nil
                Task { @MainActor in
                    await store.archive(id: reminder.id)
                }
            }
            Button("Keep reminder", role: .cancel) { reminderToArchive = nil }
        } message: {
            Text("Its future notification is canceled, and its recorded history is preserved.")
        }
        .task { await store.refreshAuthorization() }
        .alert("Reminder status", isPresented: Binding(
            get: { store.lastError != nil },
            set: { if !$0 { store.clearError() } }
        )) {
            Button("OK") { store.clearError() }
        } message: {
            Text(store.lastError ?? "")
        }
    }

    private var schedulesSection: some View {
        Section {
            if store.activeRules.isEmpty {
                Text("No repeating schedules yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(store.activeRules) { rule in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(rule.title)
                            .font(.headline)
                        Text(IOSCalendarRecurrence.summary(rule) +
                             " · " + (rule.paused ? "Paused" : "Active"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        HStack(spacing: 12) {
                            Button(rule.paused ? "Resume" : "Pause") {
                                Task { @MainActor in
                                    await store.setRoutinePaused(
                                        id: rule.id, paused: !rule.paused
                                    )
                                }
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("quietRulePause")
                            NavigationLink("History") {
                                List {
                                    let records = store.routineHistory(id: rule.id)
                                    if records.isEmpty {
                                        Text("No due occurrences recorded.")
                                    }
                                    ForEach(records) { entry in
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(entry.originalDueAt, format:
                                                .dateTime.day().month().year().hour().minute())
                                            Text(entry.outcome == .done ? "Done" :
                                                 entry.outcome == .skipped ? "Skipped" : "Unresolved")
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                .navigationTitle("Schedule history")
                            }
                            .font(.subheadline)
                            Spacer(minLength: 0)
                            Button("Archive", role: .destructive) {
                                ruleToArchive = rule
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        } header: {
            Text("Repeating schedules")
        } footer: {
            Text("Each due date has its own outcome. Changing the time of an existing recurring rule is not supported in this alpha.")
        }
    }

    private var oneTimeSection: some View {
        Section("One-time reminders") {
            let singles = store.visibleReminders.filter { $0.seriesID == nil }
            if singles.isEmpty {
                Text("No one-time reminders saved.")
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(singles.prefix(35))) { reminder in
                VStack(alignment: .leading, spacing: 8) {
                    Text(reminder.title).font(.headline)
                    Text(reminder.dueAt, format:
                        .dateTime.day().month().year().hour().minute())
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack {
                        Button("History") { selectedReminder = reminder }
                            .buttonStyle(.bordered)
                        Spacer()
                        Button("Archive", role: .destructive) {
                            reminderToArchive = reminder
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            if singles.count > 35 {
                Text("Additional records are available in the original reminder tools.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var diagnosticsSection: some View {
        Section {
            LabeledContent("Notification status") {
                Text(store.permissionStatus)
                    .multilineTextAlignment(.trailing)
            }
            Button {
                Task { await store.refreshAuthorization() }
            } label: {
                Label("Refresh permission status", systemImage: "arrow.clockwise")
            }
            Button {
                Task { await store.requestPermission() }
            } label: {
                Label("Request notification permission", systemImage: "bell")
            }
            if let url = URL(string: UIApplication.openSettingsURLString) {
                Link(destination: url) {
                    Label("Open iPhone app settings", systemImage: "gearshape")
                }
            }
            Button {
                Task { await store.reconcile() }
            } label: {
                Label("Check and repair upcoming alerts", systemImage: "clock.arrow.circlepath")
            }
            Text("iOS Focus, permissions and its pending-notification limits can affect alerts. Scheduling a notification doesn't prove it was displayed or read.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } header: {
            Text("Notification reliability")
        }
    }

    private var backupSection: some View {
        Section {
            Button {
                showingBackupTools = true
            } label: {
                Label("Open encrypted backup tools", systemImage: "externaldrive")
            }
            Text("Portable RPOSB2 export and integrity verification are available. Portable import and rollback are NOT implemented yet; existing installations should not be deleted.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } header: {
            Text("Backup and data")
        }
    }

    private var privacySection: some View {
        Section {
            Label("Offline-first, no account required", systemImage: "lock.shield")
            Text("This alpha isn't an emergency notification service. Existing reminder records stay in the local app store, unless you explicitly export a backup.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } header: {
            Text("Privacy and limitations")
        }
    }
}
