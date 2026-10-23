import Foundation

/// Pure calendar arithmetic; individual due dates are materialized as
/// independent occurrence IDs. These are not fixed 24-hour offsets.
enum IOSRepeatPattern: String, Codable, CaseIterable {
    case daily = "DAILY"
    case weekly = "WEEKLY"
    case monthly = "MONTHLY"
}

struct IOSRecurrenceRule: Codable, Identifiable, Equatable {
    var id: UUID
    var title: String
    var anchorAt: Date
    var pattern: IOSRepeatPattern
    /// Monday = bit 0 through Sunday = bit 6.
    var weekdayMask: Int
    var paused: Bool
    var archived: Bool
}

enum IOSCalendarRecurrence {
    static func slotKey(_ due: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: due)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    static func isoDay(_ date: Date, calendar: Calendar = .current) -> Int {
        (calendar.component(.weekday, from: date) + 5) % 7
    }

    /// Return next valid wall-clock slot strictly after the supplied instant.
    /// Nonexistent monthly days are skipped. For a DST spring-forward gap, the
    /// missing local time is skipped rather than silently shifted to another day.
    /// Repeated (fall-back) local times use the first matching occurrence.
    static func next(
        rule: IOSRecurrenceRule, after: Date, calendar: Calendar = .current
    ) -> Date? {
        guard rule.pattern != .weekly || (1...127).contains(rule.weekdayMask) else {
            return nil
        }
        let anchorFields = calendar.dateComponents([.day, .hour, .minute, .second], from: rule.anchorAt)
        let anchorDay = anchorFields.day ?? 1
        let hour = anchorFields.hour ?? 0
        let minute = anchorFields.minute ?? 0
        let second = anchorFields.second ?? 0
        let startingPoint = max(rule.anchorAt, after.addingTimeInterval(0.001))
        var day = calendar.startOfDay(for: startingPoint)
        for _ in 0..<4000 {
            let matches: Bool
            switch rule.pattern {
            case .daily: matches = true
            case .weekly: matches = rule.weekdayMask & (1 << isoDay(day, calendar: calendar)) != 0
            case .monthly: matches = calendar.component(.day, from: day) == anchorDay
            }
            if matches,
               let selected = calendar.date(
                   bySettingHour: hour, minute: minute, second: second, of: day,
                   matchingPolicy: .nextTime,
                   repeatedTimePolicy: .first,
                   direction: .forward
               ),
               calendar.isDate(selected, inSameDayAs: day),
               calendar.component(.hour, from: selected) == hour,
               calendar.component(.minute, from: selected) == minute,
               selected >= rule.anchorAt, selected > after {
                return selected
            }
            guard let following = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = calendar.startOfDay(for: following)
        }
        return nil
    }

    static func summary(_ rule: IOSRecurrenceRule) -> String {
        switch rule.pattern {
        case .daily: return "Daily"
        case .monthly: return "Monthly (skips missing dates)"
        case .weekly:
            let names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
            let selected = names.indices.filter { rule.weekdayMask & (1 << $0) != 0 }
            return selected.map { names[$0] }.joined(separator: ", ")
        }
    }
}
