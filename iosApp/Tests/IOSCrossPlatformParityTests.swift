import Foundation
import XCTest
@testable import ReminderOSPreview

/// The exact UTC local-date cases asserted in KMP and Android native JUnit.
/// Each platform still resolves wall-clock instants natively around DST.
final class IOSCrossPlatformParityTests: XCTestCase {
    private var utc: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        return cal
    }

    private func at(_ y: Int, _ m: Int, _ d: Int) -> Date {
        utc.date(from: DateComponents(year: y, month: m, day: d,
                                       hour: 8, minute: 30))!
    }

    private func slots(
        anchor: Date, pattern: IOSRepeatPattern, mask: Int, through: Date
    ) throws -> [String] {
        let rule = IOSRecurrenceRule(
            id: UUID(), title: "Parity", anchorAt: anchor,
            pattern: pattern, weekdayMask: mask, paused: false, archived: false
        )
        var after = anchor.addingTimeInterval(-1)
        var keys: [String] = []
        for _ in 0..<30 {
            let next = try XCTUnwrap(IOSCalendarRecurrence.next(
                rule: rule, after: after, calendar: utc
            ))
            if next > through { break }
            keys.append(IOSCalendarRecurrence.slotKey(next, calendar: utc))
            after = next
        }
        return keys
    }

    func testSharedDailyDates() throws {
        XCTAssertEqual(try slots(anchor: at(2026,10,23), pattern: .daily,
                                 mask: 0, through: at(2026,10,26)),
                       ["2026-10-23","2026-10-24","2026-10-25","2026-10-26"])
    }

    func testSharedMonWedFriMask() throws {
        XCTAssertEqual(try slots(anchor: at(2026,10,23), pattern: .weekly,
                                 mask: 21, through: at(2026,10,30)),
                       ["2026-10-23","2026-10-26","2026-10-28","2026-10-30"])
    }

    func testSharedTueThuMask() throws {
        XCTAssertEqual(try slots(anchor: at(2026,10,26), pattern: .weekly,
                                 mask: 10, through: at(2026,11,3)),
                       ["2026-10-27","2026-10-29","2026-11-03"])
    }

    func testSharedMissingMonthlyThirtyFirst() throws {
        XCTAssertEqual(try slots(anchor: at(2026,1,31), pattern: .monthly,
                                 mask: 0, through: at(2026,4,30)),
                       ["2026-01-31","2026-03-31"])
    }

    func testSharedLeapYearMonthlyTwentyNinth() throws {
        XCTAssertEqual(try slots(anchor: at(2024,1,29), pattern: .monthly,
                                 mask: 0, through: at(2024,3,31)),
                       ["2024-01-29","2024-02-29","2024-03-29"])
    }
}
