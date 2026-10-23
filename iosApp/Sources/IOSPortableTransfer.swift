import Foundation
import ReminderCore

/// Cross-platform portable JSON payload v1, protected by RPOSB2 encryption.
/// All operations are in-memory; there is NO portable import writer.
enum IOSPortableTransfer {
    static let format=1

    static func encode(
        rules: [IOSRecurrenceRule], reminders: [IOSReminder],
        events: [IOSReminderEvent]
    ) throws -> Data {
        let lookup=Dictionary(uniqueKeysWithValues:rules.map { ($0.id,$0) })
        let ruleRows: [[String:Any]]=rules.map { r in [
            "id":r.id.uuidString, "title":r.title,
            "anchor_at_ms":Int64(r.anchorAt.timeIntervalSince1970*1000),
            "cadence":r.pattern.rawValue,
            "weekday_mask":r.pattern == .weekly ? r.weekdayMask : 0,
            "paused":r.paused, "archived":r.archived
        ]}
        let reminderRows: [[String:Any]]=reminders.map { r in [
            "id":r.id.uuidString, "title":r.title,
            "due_at_ms":Int64(r.dueAt.timeIntervalSince1970*1000),
            "original_due_at_ms":Int64(r.originalDueAt.timeIntervalSince1970*1000),
            "revision":r.revision, "outcome":r.outcome.rawValue,
            "paused":r.seriesID.flatMap { lookup[$0]?.paused } ?? false,
            "archived":r.archived,
            "series_id":r.seriesID.map { $0.uuidString } as Any? ?? NSNull(),
            "slot_key":r.slotKey as Any? ?? NSNull()
        ]}
        let eventRows: [[String:Any]]=events.map { e in [
            "id":e.id.uuidString, "reminder_id":e.reminderID.uuidString,
            "kind":e.kind, "revision":e.revision,
            "at_ms":Int64(e.recordedAt.timeIntervalSince1970*1000)
        ]}
        let result=try JSONSerialization.data(withJSONObject:[
            "portable_format":format,
            "rules":ruleRows, "reminders":reminderRows, "events":eventRows
        ],options:[.sortedKeys])
        guard result.count>0 && result.count<=IOSPortableBackupCrypto.maxPlaintext else {
            throw NSError(domain:"ReminderOS",code:61,userInfo:[
                NSLocalizedDescriptionKey:"Portable backup size limit exceeded."
            ])
        }
        _ = try inspectPayload(result)
        return result
    }

    private static func error(_ explanation: String) -> NSError {
        NSError(domain:"ReminderOS",code:62,userInfo:[
            NSLocalizedDescriptionKey:explanation
        ])
    }

    /// Strictly parses and checks entire portable snapshot without any writes.
    /// Import of this file into the current app is not implemented.
    static func inspectPayload(_ data: Data) throws -> String {
        guard data.count > 0 && data.count <= IOSPortableBackupCrypto.maxPlaintext,
              let root=try JSONSerialization.jsonObject(with:data) as? [String:Any],
              (root["portable_format"] as? NSNumber)?.intValue == format,
              let rules=root["rules"] as? [[String:Any]],
              let reminders=root["reminders"] as? [[String:Any]],
              let events=root["events"] as? [[String:Any]],
              rules.count<=3000 && reminders.count<=30000 && events.count<=100000 else {
            throw error("Invalid or unsupported portable snapshot")
        }
        let bridge=StorageMigrationBridge()
        func str(_ row: [String:Any], _ field: String) throws -> String {
            guard let value=row[field] as? String else { throw error("Missing field: \(field)") }
            return value
        }
        func int(_ row: [String:Any], _ field: String) throws -> Int64 {
            guard let value=row[field] as? NSNumber else { throw error("Missing number: \(field)") }
            return value.int64Value
        }
        func flag(_ row: [String:Any], _ field: String) throws -> Bool {
            guard let value=row[field] as? Bool else { throw error("Missing flag: \(field)") }
            return value
        }
        func optional(_ row: [String:Any], _ field: String) throws -> String? {
            guard let value=row[field] else { throw error("Missing field: \(field)") }
            if value is NSNull { return nil }
            guard let string=value as? String else { throw error("Invalid field: \(field)") }
            return string
        }
        var ruleIDs=Set<String>()
        for rule in rules {
            let id=try str(rule,"id")
            guard ruleIDs.insert(id).inserted,
                  bridge.validateRuleFields(
                    id:id,title:try str(rule,"title"),
                    anchorEpochMillis:try int(rule,"anchor_at_ms"),
                    cadence:try str(rule,"cadence"),
                    weekdayMask:Int32(try int(rule,"weekday_mask"))
                  ) else { throw error("Invalid or duplicate recurring schedule") }
            _=try flag(rule,"paused")
            _=try flag(rule,"archived")
        }
        var reminderIDs=Set<String>()
        var logicalSlots=Set<String>()
        var unresolved=0
        for reminder in reminders {
            let id=try str(reminder,"id")
            let outcome=try str(reminder,"outcome")
            let series=try optional(reminder,"series_id")
            let slot=try optional(reminder,"slot_key")
            guard reminderIDs.insert(id).inserted,
                  bridge.validateReminderFields(
                    id:id,title:try str(reminder,"title"),
                    dueEpochMillis:try int(reminder,"due_at_ms"),
                    originalDueEpochMillis:try int(reminder,"original_due_at_ms"),
                    revision:Int32(try int(reminder,"revision")),outcome:outcome,
                    seriesId:series,slotKey:slot
                  ) else { throw error("Invalid or duplicate reminder occurrence") }
            _=try flag(reminder,"paused")
            _=try flag(reminder,"archived")
            if outcome == "UNKNOWN" { unresolved += 1 }
            if let series, let slot {
                guard ruleIDs.contains(series),
                      logicalSlots.insert(series+"/"+slot).inserted else {
                    throw error("Duplicate calendar occurrence or orphaned series")
                }
            }
        }
        var eventIDs=Set<String>()
        for event in events {
            let id=try str(event,"id")
            guard !id.isEmpty && id.count <= 128 && eventIDs.insert(id).inserted,
                  reminderIDs.contains(try str(event,"reminder_id")),
                  try !str(event,"kind").isEmpty,
                  try str(event,"kind").count<=64,
                  try int(event,"revision")>=1,
                  try int(event,"at_ms")>=0 else {
                throw error("Invalid or orphaned action history")
            }
        }
        return "Verified portable encrypted snapshot (RPOSB2)\n" +
               "Schedules: \(rules.count)\nOccurrences: \(reminders.count)\n" +
               "Unanswered: \(unresolved)\nHistory: \(events.count)\n" +
               "No reminder has been imported or changed."
    }

    static func inspectEncrypted(_ encrypted: Data, passphrase: String) throws -> String {
        let plain=try IOSPortableBackupCrypto.decrypt(encrypted,passphrase:passphrase)
        return try inspectPayload(plain)
    }
}
