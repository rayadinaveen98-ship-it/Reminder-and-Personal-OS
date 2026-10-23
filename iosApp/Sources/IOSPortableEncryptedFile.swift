import Foundation
import SwiftUI
import UniformTypeIdentifiers

/// FileDocument wrapper only receives already-encrypted RPOSB2 bytes.
/// No plaintext JSON is ever handed to the iOS file export UI.
struct IOSPortableEncryptedFile: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var encryptedBytes: Data

    init(encryptedBytes: Data) { self.encryptedBytes = encryptedBytes }

    init(configuration: ReadConfiguration) throws {
        guard let bytes=configuration.file.regularFileContents,
              bytes.count<=IOSPortableBackupCrypto.maxFile else {
            throw CocoaError(.fileReadCorruptFile)
        }
        encryptedBytes=bytes
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents:encryptedBytes)
    }
}
