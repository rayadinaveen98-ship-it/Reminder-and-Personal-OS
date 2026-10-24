import SwiftUI

/// R3b: Quiet Precision native create flow. Uses the original iOS store and
/// notification reconciliation, not a second data path or speculative sync.
struct QuietPrecisionReminderEditor: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = IOSReminderStore.shared

    @State private var title = ""
    @State private var dueAt = Date().addingTimeInterval(3600)
    @State private var repeatChoice = "Once"
    @State private var weekdays = 31 // ISO Monday = bit 0
    @State private var saving = false
    @State private var validation: String?
    private let choices = ["Once", "Daily", "Weekly", "Monthly"]
    private let dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday",
                            "Friday", "Saturday", "Sunday"]
    private let blue = Color(red: 0.239, green: 0.388, blue: 0.812)

    private var valid: Bool {
        let name = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return !name.isEmpty && name.count <= 200 &&
               dueAt > Date().addingTimeInterval(2) &&
               (repeatChoice != "Weekly" || (1...127).contains(weekdays))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("What do you need to remember?", text: $title)
                        .textInputAutocapitalization(.sentences)
                        .submitLabel(.done)
                        .accessibilityIdentifier("quietReminderTitle")
                    DatePicker("Due", selection: $dueAt, in: Date()...,
                               displayedComponents: [.date, .hourAndMinute])
                        .accessibilityIdentifier("quietReminderDue")
                } header: {
                    Text("Reminder")
                } footer: {
                    Text("A reminder is saved locally before its notification is scheduled.")
                }

                Section("Repeat") {
                    Picker("Schedule", selection: $repeatChoice) {
                        ForEach(choices, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("quietReminderRepeat")
                    if repeatChoice == "Weekly" {
                        ForEach(dayNames.indices, id: \.self) { index in
                            Toggle(dayNames[index], isOn: Binding(
                                get: { weekdays & (1 << index) != 0 },
                                set: { selected in
                                    if selected { weekdays |= 1 << index }
                                    else { weekdays &= ~(1 << index) }
                                }
                            ))
                        }
                    }
                    if repeatChoice != "Once" {
                        Text("Each repeating date keeps a separate outcome. An unanswered reminder remains unresolved.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Button {
                        saveReminder()
                    } label: {
                        HStack {
                            Spacer()
                            if saving {
                                ProgressView().tint(.white)
                            } else {
                                Text("Save reminder").font(.headline)
                            }
                            Spacer()
                        }
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(blue)
                    .disabled(!valid || saving)
                    .accessibilityIdentifier("quietReminderSave")
                } footer: {
                    Text("Delivery depends on notification permission and the operating system. No account is required.")
                }

                if let validation {
                    Section {
                        Label(validation, systemImage: "exclamationmark.circle")
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("quietReminderError")
                    }
                }
            }
            .navigationTitle("New reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                        .disabled(saving)
                }
            }
        }
    }

    private func saveReminder() {
        guard valid && !saving else {
            validation = "Enter a title, a future time and valid weekly days."
            return
        }
        saving = true
        validation = nil
        let cleaned = title.trimmingCharacters(in: .whitespacesAndNewlines)
        Task { @MainActor in
            let saved: Bool
            switch repeatChoice {
            case "Daily", "Weekly", "Monthly":
                let pattern: IOSRepeatPattern = repeatChoice == "Daily" ? .daily :
                    (repeatChoice == "Weekly" ? .weekly : .monthly)
                saved = await store.createRecurring(
                    title: cleaned, dueAt: dueAt,
                    pattern: pattern, weekdayMask: weekdays
                )
            default:
                saved = await store.create(title: cleaned, dueAt: dueAt)
            }
            saving = false
            if saved {
                dismiss()
            } else {
                validation = store.lastError ?? "The reminder could not be saved."
            }
        }
    }
}
