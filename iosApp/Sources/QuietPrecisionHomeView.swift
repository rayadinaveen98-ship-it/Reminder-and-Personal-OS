import SwiftUI

/// Quiet Precision R3 UI shell, backed by the existing native reminder store.
/// The alpha's existing editor, recurrence tools and portable export/verify
/// remain available through "Reminder tools". No backup import or analytics
/// engine is implied by this navigation.
struct QuietPrecisionHomeView: View {
    @ObservedObject private var store = IOSReminderStore.shared
    @State private var selected: Destination = .today
    @State private var showingTools = false
    @State private var showingManagement = false
    @State private var showingHistory = false
    @State private var showingEditor = false
    @State private var selectedReminder: IOSReminder?
    @State private var pendingSkip: IOSReminder?

    private enum Destination: String, CaseIterable {
        case today = "Today"
        case track = "Track"
        case insights = "Insights"
        case library = "Library"

        var icon: String {
            switch self {
            case .today: return "house"
            case .track: return "waveform.path.ecg"
            case .insights: return "chart.bar"
            case .library: return "square.grid.2x2"
            }
        }
    }

    private enum Design {
        static let blue = Color(red: 0.239, green: 0.388, blue: 0.812)
        static let canvas = Color(red: 0.965, green: 0.973, blue: 0.988)
        static let ink = Color(red: 0.114, green: 0.161, blue: 0.216)
        static let secondary = Color(red: 0.373, green: 0.431, blue: 0.502)
        static let success = Color(red: 0.098, green: 0.431, blue: 0.333)
        static let caution = Color(red: 0.600, green: 0.357, blue: 0.118)
    }

    private var visible: [IOSReminder] {
        store.visibleReminders.sorted { $0.dueAt < $1.dueAt }
    }
    private var unknown: [IOSReminder] {
        visible.filter { $0.outcome == .unknown }
    }

    var body: some View {
        TabView(selection: $selected) {
            NavigationStack { todayScreen }
                .tabItem { Label("Today", systemImage: Destination.today.icon) }
                .tag(Destination.today)
            NavigationStack { trackScreen }
                .tabItem { Label("Track", systemImage: Destination.track.icon) }
                .tag(Destination.track)
            NavigationStack { insightsScreen }
                .tabItem { Label("Insights", systemImage: Destination.insights.icon) }
                .tag(Destination.insights)
            NavigationStack { libraryScreen }
                .tabItem { Label("Library", systemImage: Destination.library.icon) }
                .tag(Destination.library)
        }
        .tint(Design.blue)
        .sheet(isPresented: $showingTools) {
            PreviewContentView()
        }
        .sheet(isPresented: $showingManagement) {
            QuietPrecisionManagementView()
        }
        .sheet(isPresented: $showingHistory) {
            QuietPrecisionHistoryView()
        }
        .sheet(isPresented: $showingEditor) {
            QuietPrecisionReminderEditor()
        }
        .sheet(item: $selectedReminder) { reminder in
            QuietPrecisionReminderDetail(reminder: reminder)
        }
        .confirmationDialog(
            "Skip this occurrence?",
            isPresented: Binding(
                get: { pendingSkip != nil },
                set: { if !$0 { pendingSkip = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Skip occurrence", role: .destructive) {
                guard let reminder = pendingSkip else { return }
                pendingSkip = nil
                respond(reminder, "SKIP")
            }
            Button("Cancel", role: .cancel) { pendingSkip = nil }
        } message: {
            Text("Only this reminder occurrence is skipped. Future repeats remain independent.")
        }
        .task {
            await store.refreshAuthorization()
            await store.reconcile()
        }
        .alert("Reminder status", isPresented: Binding(
            get: { store.lastError != nil },
            set: { if !$0 { store.clearError() } }
        )) {
            Button("OK") { store.clearError() }
        } message: {
            Text(store.lastError ?? "")
        }
    }

    private func shell<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                content()
            }
            .padding(20)
            .frame(maxWidth: 650, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showingManagement = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .accessibilityLabel("Reminder tools and settings")
            }
        }
    }

    private var todayScreen: some View {
        shell(title: "Today") {
            Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("Your day, in focus")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.primary)
                .accessibilityAddTraits(.isHeader)

            if let first = unknown.first(where: { $0.dueAt >= .now }) {
                Text("NEXT UP")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                reminderCard(first)
            } else {
                card {
                    Text("Nothing upcoming")
                        .font(.headline)
                    Text("Start with one reminder.")
                        .foregroundStyle(.secondary)
                    primaryAction("Add reminder", systemImage: "plus")
                }
            }

            let overdue = unknown.filter { $0.dueAt < .now }
            Text(overdue.isEmpty ? "More reminders" : "Unresolved · \(overdue.count)")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)

            let nextItems = overdue.isEmpty ?
                Array(unknown.filter { $0.dueAt >= .now }.dropFirst().prefix(3)) :
                Array(overdue.prefix(3))
            if nextItems.isEmpty {
                Text("No other reminders to show.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(nextItems) { reminder in
                    reminderCard(reminder)
                }
            }
            primaryAction("New reminder", systemImage: "plus")
            Text("No response is recorded as unresolved, not skipped.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private func reminderCard(_ reminder: IOSReminder) -> some View {
        card {
            VStack(alignment: .leading, spacing: 11) {
                Text(reminder.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(reminder.dueAt, format: .dateTime.day().month().hour().minute())
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                let overdue = reminder.outcome == .unknown && reminder.dueAt < .now
                Text(reminder.outcome == .done ? "Done" :
                     reminder.outcome == .skipped ? "Skipped" :
                     overdue ? "Overdue · no response recorded" : "Upcoming")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(overdue ? Design.caution :
                        reminder.outcome == .done ? Design.success : Design.secondary)
                if reminder.outcome == .unknown {
                    HStack(spacing: 8) {
                        Button("Done") { respond(reminder, "DONE") }
                            .buttonStyle(.borderedProminent)
                        Button("Snooze") { respond(reminder, "SNOOZE") }
                            .buttonStyle(.bordered)
                        Button("Skip") { pendingSkip = reminder }
                            .buttonStyle(.bordered)
                    }
                    .controlSize(.regular)
                    .tint(Design.blue)
                }
                Button("History") { selectedReminder = reminder }
                    .font(.footnote.weight(.medium))
                    .accessibilityIdentifier("quietReminderHistory")
            }
        }
    }

    private var trackScreen: some View {
        shell(title: "Track") {
            card {
                Label("Activities and measurements", systemImage: "waveform.path.ecg")
                    .font(.headline)
                Text("Tracking is part of the next implementation phase. It is not available in this alpha.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var insightsScreen: some View {
        shell(title: "Insights") {
            let all = visible
            let done = all.filter { $0.outcome == .done }.count
            let skipped = all.filter { $0.outcome == .skipped }.count
            let unresolved = all.filter { $0.outcome == .unknown }.count
            card {
                Text("All visible reminder occurrences")
                    .font(.headline)
                Text("Confirmed Done: \(done)")
                    .foregroundStyle(Design.success)
                Text("Explicitly Skipped: \(skipped)")
                Text("Unresolved: \(unresolved)")
                    .foregroundStyle(Design.caution)
                Text("These are current visible records, not 7/28/90-day analytics. No-response is not failure.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Text("Evidence-backed trends and personal insights are not implemented yet.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var libraryScreen: some View {
        shell(title: "Library") {
            if !store.activeRules.isEmpty {
                Text("Repeating schedules")
                    .font(.headline)
                ForEach(store.activeRules) { rule in
                    card {
                        Text(rule.title)
                            .font(.headline)
                        Text(rule.paused ? "Paused" : "Active")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Text("Reminders")
                .font(.headline)
            if visible.isEmpty {
                Text("No reminders saved yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(visible.prefix(40))) { item in
                    reminderCard(item)
                }
            }
            Button {
                showingHistory = true
            } label: {
                Label("Browse History & Archive", systemImage: "archivebox")
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.bordered)
            Button {
                showingManagement = true
            } label: {
                Label("Manage reminders and settings", systemImage: "square.stack")
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Design.blue)
        }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func primaryAction(_ label: String, systemImage: String,
                               opensTools: Bool = false) -> some View {
        Button {
            if opensTools { showingTools = true }
            else { showingEditor = true }
        } label: {
            Label(label, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 48)
        }
        .buttonStyle(.borderedProminent)
        .tint(Design.blue)
    }

    private func respond(_ reminder: IOSReminder, _ action: String) {
        Task { @MainActor in
            _ = await store.respond(id: reminder.id, revision: reminder.revision, action: action)
        }
    }
}
