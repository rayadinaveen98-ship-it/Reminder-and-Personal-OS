import SwiftUI

struct PreviewContentView: View {
    private let scheduler = PreviewNotificationScheduler()
    @State private var status = "Check your notification permission and schedule a one-minute test."
    @State private var permission = "Checking…"
    @State private var pending = "Checking…"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Native notification preview")
                        .font(.title2.bold())
                    Text("This validates local notification scheduling. It is not the full reminder and tracking product.")
                        .foregroundStyle(.secondary)
                    Text("Permission: \(permission)")
                    Text("Status: \(pending)")
                    Text(status)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("previewStatus")
                    Button("Request notification permission") {
                        Task {
                            status = await scheduler.requestPermission()
                            await refresh()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Schedule test in 60 seconds") {
                        Task {
                            status = await scheduler.scheduleTest()
                            await refresh()
                        }
                    }
                    .buttonStyle(.bordered)
                    Button("Cancel test reminder") {
                        Task {
                            status = await scheduler.cancelTest()
                            await refresh()
                        }
                    }
                    .buttonStyle(.bordered)
                }
                .padding(24)
            }
            .navigationTitle("Reminder OS Preview")
            .task { await refresh() }
        }
    }

    @MainActor
    private func refresh() async {
        permission = await scheduler.permissionSummary()
        pending = await scheduler.pendingSummary()
    }
}
