import Foundation
import XCTest
@testable import ReminderOSPreview

final class IOSPortableEncryptedBackupTests: XCTestCase {
    private let passphrase="Portable test password 2026"
    /// Independent Python hashlib.pbkdf2_hmac + cryptography AESGCM fixture.
    private var fixture: Data {
        Data(base64Encoded:"UlBPU0IyAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG1x4GCFNhRHr9qELisfDotqvh9PfeoIYMPgfzQTDA3nug0Ro0VVthqOAfCdvlOX9fut2S76NUGjBlvG33KCHnWIfgOM2Pk2Ob4jgiSrWxHu4qjC7SUhZ/EIpzAHNzM/ajtXK1Q243qpQHopO8JRYMlaw4byBVTK9AyA3lnzqHlwRNm1YKDqJ2T/96J8PiNXWGXhchSzl+Mhbcjd80b5L4id3SROC8hTc3kKPlfDMzWuY3vB+Q50bc64Cfnpb6VL+QrdHPaxYs3BV3bWaRomHh4/PdbBqiY7SB8jS8j04oGMBK2JFNHt6yjz2/loSkTiV5l2wS0kbVrnRklT7dNZ1qzmZ7NKnS6EQJxTIJ3V/IdCGKsZd51RiuGFs0Q==")!
    }

    func testCrossPlatformSyntheticFixtureDecrypts() throws {
        let raw=try IOSPortableBackupCrypto.decrypt(fixture,passphrase:passphrase)
        let report=try IOSPortableTransfer.inspectPayload(raw)
        XCTAssertTrue(report.contains("Occurrences: 1"))
        XCTAssertTrue(report.contains("Unanswered: 1"))
        let object=try XCTUnwrap(JSONSerialization.jsonObject(with:raw) as? [String:Any])
        let rows=try XCTUnwrap(object["reminders"] as? [[String:Any]])
        XCTAssertEqual(rows.first?["title"] as? String,"Test reminder")
    }

    func testTamperedFixtureAndWrongPasswordRejected() throws {
        var broken=fixture
        broken[broken.index(before:broken.endIndex)] ^= 1
        XCTAssertThrowsError(try IOSPortableBackupCrypto.decrypt(
            broken,passphrase:passphrase
        ))
        XCTAssertThrowsError(try IOSPortableBackupCrypto.decrypt(
            fixture,passphrase:"Wrong password for test"
        ))
        XCTAssertThrowsError(try IOSPortableBackupCrypto.decrypt(
            Data("RPOSB1".utf8)+fixture.dropFirst(6),passphrase:passphrase
        ))
    }

    func testNativePortableExportRoundTripAndNoPlaintextLeak() throws {
        let original=Data("""
        {"events":[],"portable_format":1,"reminders":[],"rules":[]}
        """.utf8)
        let file=try IOSPortableBackupCrypto.encrypt(original,passphrase:passphrase)
        XCTAssertFalse(file.contains(Data("portable_format".utf8)))
        let decrypted=try IOSPortableBackupCrypto.decrypt(file,passphrase:passphrase)
        XCTAssertEqual(original,decrypted)
        XCTAssertTrue(try IOSPortableTransfer.inspectEncrypted(
            file,passphrase:passphrase
        ).contains("Occurrences: 0"))
    }

    @MainActor
    func testActualNativeStoreExportsWithoutChangingJsonFile() async throws {
        let folder=FileManager.default.temporaryDirectory
            .appendingPathComponent("portable-crypto-\(UUID().uuidString)",isDirectory:true)
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        defer { try? FileManager.default.removeItem(at:folder) }
        let file=folder.appendingPathComponent("reminders.json")
        let store=IOSReminderStore(storageURL:file)
        let added=await store.create(title:"Do something",dueAt:.now.addingTimeInterval(3600))
        XCTAssertTrue(added)
        let original=try Data(contentsOf:file)
        let encrypted=try store.exportPortableEncrypted(passphrase:passphrase)
        let report=try IOSPortableTransfer.inspectEncrypted(encrypted,passphrase:passphrase)
        XCTAssertTrue(report.contains("Occurrences: 1"))
        XCTAssertEqual(original,try Data(contentsOf:file))
    }
}
