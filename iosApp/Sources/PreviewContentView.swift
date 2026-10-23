import SwiftUI
import UniformTypeIdentifiers

/// iOS V0.4a: native persistent one-time reminders, not yet full KMP parity.
struct PreviewContentView: View {
    @ObservedObject private var store = IOSReminderStore.shared
    @State private var portablePassphrase = ""
    @State private var portableConfirm = ""
    @State private var portableExport: IOSPortableEncryptedFile? = nil
    @State private var showingPortableExport = false
    @State private var showingPortableVerify = false
    @State private var title = ""
    @State private var dueAt = Date().addingTimeInterval(5 * 60)
    @State private var busy = false
    @State private var migrationReport: String? = nil
    @State private var repeatChoice = "Once"
    @State private var weeklyDaysMask = 31
    private let weekdayNames = ["Monday", "Tuesday", "Wednesday", "Thursday",
                                "Friday", "Saturday", "Sunday"]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Reminder & Personal OS")
                        .font(.title2.bold())
                    Text("iOS V0.5c · Portable data inspection")
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
                    Picker("Repeat", selection: $repeatChoice) {
                        Text("Once").tag("Once")
                        Text("Daily").tag("Daily")
                        Text("Weekly").tag("Weekly")
                        Text("Monthly").tag("Monthly")
                    }
                    if repeatChoice == "Weekly" {
                        ForEach(weekdayNames.indices, id: \.self) { index in
                            Toggle(weekdayNames[index], isOn: Binding(
                                get: { weeklyDaysMask & (1 << index) != 0 },
                                set: { selected in
                                    if selected { weeklyDaysMask |= 1 << index }
                                    else { weeklyDaysMask &= ~(1 << index) }
                                }
                            ))
                        }
                    }
                    Button("Save reminder") {
                        Task {
                            busy = true
                            let saved: Bool
                            switch repeatChoice {
                            case "Daily", "Weekly", "Monthly":
                                let mode: IOSRepeatPattern = repeatChoice == "Daily" ? .daily :
                                    (repeatChoice == "Weekly" ? .weekly : .monthly)
                                saved = await store.createRecurring(
                                    title: title, dueAt: dueAt, pattern: mode,
                                    weekdayMask: weeklyDaysMask
                                )
                            default:
                                saved = await store.create(title: title, dueAt: dueAt)
                            }
                            if saved {
                                title = ""
                                dueAt = Date().addingTimeInterval(5 * 60)
                                repeatChoice = "Once"
                            }
                            busy = false
                        }
                    }
                    .disabled(busy || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier("saveReminder")
                }
                if !store.activeRules.isEmpty {
                    Section("Repeating schedules") {
                        ForEach(store.activeRules) { rule in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(rule.title).font(.headline)
                                Text(IOSCalendarRecurrence.summary(rule))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                Text(rule.paused ? "Paused" : "Active")
                                    .font(.caption)
                                HStack {
                                    Button(rule.paused ? "Resume" : "Pause") {
                                        Task {
                                            await store.setRoutinePaused(id: rule.id,
                                                                         paused: !rule.paused)
                                        }
                                    }
                                    NavigationLink("History") {
                                        List(store.routineHistory(id: rule.id)) { instance in
                                            VStack(alignment: .leading) {
                                                Text(instance.originalDueAt, style: .date)
                                                Text(instance.outcome.rawValue)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                        .navigationTitle("Occurrence history")
                                    }
                                    Button("Archive", role: .destructive) {
                                        Task { await store.archiveRoutine(id: rule.id) }
                                    }
                                }
                                .font(.caption)
                                .buttonStyle(.bordered)
                            }
                        }
                    }
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
                            if reminder.seriesID != nil {
                                Text("Repeating · \(reminder.slotKey ?? "")")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
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
                            if reminder.seriesID == nil {
                                Button("Archive", role: .destructive) {
                                    Task { await store.archive(id: reminder.id) }
                                }
                                .font(.caption)
                            }
                        }
                        .padding(.vertical, 5)
                    }
                }
                Section {
                    SecureField("Portable backup passphrase (12+ ASCII)",text:$portablePassphrase)
                    SecureField("Confirm portable backup passphrase",text:$portableConfirm)
                    Button("Export encrypted portable backup") {
                        guard portablePassphrase == portableConfirm else {
                            migrationReport="Passphrases do not match; nothing was exported."
                            return
                        }
                        do {
                            let encrypted=try store.exportPortableEncrypted(
                                passphrase:portablePassphrase
                            )
                            portableExport=IOSPortableEncryptedFile(encryptedBytes:encrypted)
                            showingPortableExport=true
                        } catch {
                            migrationReport="Portable export failed: " + error.localizedDescription
                        }
                    }
                    Button("Verify portable backup without importing") {
                        showingPortableVerify=true
                    }
                    Button("Inspect data migration readiness") {
                        do {
                            migrationReport = try store.inspectMigrationReadiness()
                        } catch {
                            migrationReport = "Migration check failed: " + error.localizedDescription +
                                ". No existing reminders were changed."
                        }
                    }
                    if let migrationReport {
                        Text(migrationReport)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Button("Check & repair future alerts") {
                        Task { await store.reconcile() }
                    }
                    Text("Each repeating date has its own outcome. Unanswered reminders remain unresolved; this alpha pre-schedules a limited rolling window and needs the app reopened to replenish after long absences. iOS Focus and notification settings still affect delivery.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Reminder OS")
            .task {
                await store.refreshAuthorization()
                await store.reconcile()
            }
            .fileExporter(
                isPresented:$showingPortableExport,
                document:portableExport,
                contentType:.data,
                defaultFilename:"reminder-os-portable-v2.rpos"
            ) { result in
                switch result {
                case .success:
                    migrationReport="Encrypted portable backup saved. Keep your password safe."
                case .failure(let error):
                    migrationReport="Export canceled or failed: " + error.localizedDescription
                }
                portableExport=nil
            }
            .fileImporter(
                isPresented:$showingPortableVerify,
                allowedContentTypes:[.data],
                allowsMultipleSelection:false
            ) { result in
                do {
                    let url=try result.get().first!
                    let scoped=url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    let size=try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                    guard size>0 && size<=IOSPortableBackupCrypto.maxFile else {
                        throw CocoaError(.fileReadTooLarge)
                    }
                    let bytes=try Data(contentsOf:url)
                    migrationReport=try IOSPortableTransfer.inspectEncrypted(
                        bytes,passphrase:portablePassphrase
                    )
                } catch {
                    migrationReport="Portable verification failed: " +
                        error.localizedDescription + ". Nothing was imported."
                }
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
