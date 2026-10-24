import SwiftUI

/// R3e native read-only archive/history explorer. All rows come from the
/// persisted store (not the condensed Today projection) and are searchable.
struct QuietPrecisionHistoryView: View {
    @ObservedObject private var store = IOSReminderStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var archiveOnly = false
    @State private var search = ""
    @State private var outcome = "ALL"
    @State private var visibleLimit = 25
    @State private var selectedReminder: IOSReminder?

    private var selectedOutcome: IOSReminder.Outcome? {
        switch outcome {
        case "DONE": return .done
        case "SKIPPED": return .skipped
        case "UNKNOWN": return .unknown
        default: return nil
        }
    }
    private var matching: [IOSReminder] {
        store.historyRecords(archiveOnly: archiveOnly, query: search,
                             outcome: selectedOutcome)
    }
    private var displayed: [IOSReminder] {
        Array(matching.prefix(visibleLimit))
    }
    private var archivedRuleIDs: Set<UUID> {
        Set(store.archivedRules.map(\.id))
    }
    private func isArchived(_ row: IOSReminder) -> Bool {
        row.archived || (row.seriesID.map { archivedRuleIDs.contains($0) } ?? false)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Browse", selection: $archiveOnly) {
                        Text("History").tag(false)
                        Text("Archive").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("quietHistoryScope")
                    Picker("Outcome", selection: $outcome) {
                        Text("All").tag("ALL")
                        Text("Unresolved").tag("UNKNOWN")
                        Text("Done").tag("DONE")
                        Text("Skipped").tag("SKIPPED")
                    }
                    .accessibilityIdentifier("quietHistoryOutcome")
                }
                Section {
                    if matching.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Label(archiveOnly ? "No archived reminders" :
                                  "No matching history", systemImage: "archivebox")
                                .font(.headline)
                            Text("Try another title, outcome or category.")
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 20)
                    } else {
                        ForEach(displayed) { record in
                            Button {
                                selectedReminder = record
                            } label: {
                                VStack(alignment: .leading, spacing: 7) {
                                    Text(record.title)
                                        .font(.headline)
                                        .foregroundStyle(.primary)
                                    Text(record.originalDueAt, format:
                                        .dateTime.day().month().year().hour().minute())
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                    HStack(spacing: 8) {
                                        Text(record.outcome == .done ? "Done" :
                                             record.outcome == .skipped ? "Skipped" : "Unresolved")
                                        if isArchived(record) {
                                            Text("· Archived")
                                        }
                                    }
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 4)
                            }
                            .accessibilityIdentifier("quietHistoryRecord")
                        }
                        if matching.count > displayed.count {
                            Button("Load more") {
                                visibleLimit += 25
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .accessibilityIdentifier("quietHistoryLoadMore")
                        }
                    }
                } header: {
                    Text("Recorded occurrences")
                } footer: {
                    Text("Showing \(displayed.count) of \(matching.count) matching records. Unanswered is not skipped or completed.")
                }
            }
            .navigationTitle(archiveOnly ? "Archive" : "History")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $search, prompt: "Search reminder title")
            .onChange(of: archiveOnly) { _ in visibleLimit = 25 }
            .onChange(of: outcome) { _ in visibleLimit = 25 }
            .onChange(of: search) { _ in visibleLimit = 25 }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .sheet(item: $selectedReminder) { reminder in
            QuietPrecisionReminderDetail(reminder: reminder)
        }
    }
}
