import Foundation
import CommonCrypto
import CryptoKit
import Security

/// RPOSB2: UTF-8 "RPOSB2" + 16 salt + 12 nonce + AES-256-GCM ciphertext/tag.
/// AAD authenticates the entire header; PBKDF2-HMAC-SHA256 uses 210,000 rounds.
/// Separate from the existing Android RPOSB1 backup, which is not modified.
enum IOSPortableBackupCrypto {
    private static let magic=Data("RPOSB2".utf8)
    private static let saltLength=16
    private static let nonceLength=12
    private static let tagLength=16
    static let maxPlaintext=8*1024*1024
    static let maxFile=maxPlaintext+4096
    static let iterations=210_000

    private static func validatePassword(_ passphrase: String) throws {
        let scalars=passphrase.unicodeScalars
        guard scalars.count >= 12 && scalars.count <= 128 &&
              scalars.allSatisfy({ $0.value >= 32 && $0.value <= 126 }) else {
            throw NSError(domain:"ReminderOS",code:51,userInfo:[
                NSLocalizedDescriptionKey:"Portable backup passphrase must be 12–128 printable ASCII characters."
            ])
        }
    }

    private static func randomBytes(count: Int) throws -> Data {
        var bytes=[UInt8](repeating:0,count:count)
        guard SecRandomCopyBytes(kSecRandomDefault,count,&bytes)==errSecSuccess else {
            throw NSError(domain:"ReminderOS",code:52,userInfo:[
                NSLocalizedDescriptionKey:"Unable to generate secure random backup salt/nonce."
            ])
        }
        return Data(bytes)
    }

    private static func key(passphrase: String, salt: Data) throws -> SymmetricKey {
        try validatePassword(passphrase)
        var result=[UInt8](repeating:0,count:32)
        let passwordBytes=Array(passphrase.utf8)
        let resultLength=result.count
        let status: Int32 = passwordBytes.withUnsafeBufferPointer { pw in
            salt.withUnsafeBytes { saltRaw in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                    UnsafeRawPointer(pw.baseAddress!).assumingMemoryBound(to: Int8.self),
                    passwordBytes.count,
                    saltRaw.bindMemory(to: UInt8.self).baseAddress,
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    UInt32(iterations), &result, resultLength)
            }
        }
        guard status==kCCSuccess else {
            throw NSError(domain:"ReminderOS",code:53,userInfo:[
                NSLocalizedDescriptionKey:"Could not derive encrypted backup key."
            ])
        }
        defer { result.withUnsafeMutableBufferPointer { buffer in
            for i in buffer.indices { buffer[i]=0 }
        } }
        return SymmetricKey(data:Data(result))
    }

    static func encrypt(_ plaintext: Data, passphrase: String) throws -> Data {
        guard plaintext.count > 0 && plaintext.count <= maxPlaintext else {
            throw NSError(domain:"ReminderOS",code:54,userInfo:[
                NSLocalizedDescriptionKey:"Portable backup is empty or exceeds size limit."
            ])
        }
        let salt=try randomBytes(count:saltLength)
        let nonceData=try randomBytes(count:nonceLength)
        let header=magic+salt+nonceData
        let sealed=try AES.GCM.seal(plaintext,using:key(passphrase:passphrase,salt:salt),
                                  nonce:AES.GCM.Nonce(data:nonceData),authenticating:header)
        return header+sealed.ciphertext+sealed.tag
    }

    static func decrypt(_ file: Data, passphrase: String) throws -> Data {
        let headerLength=magic.count+saltLength+nonceLength
        guard file.count >= headerLength+tagLength && file.count <= maxFile,
              file.prefix(magic.count)==magic else {
            throw NSError(domain:"ReminderOS",code:55,userInfo:[
                NSLocalizedDescriptionKey:"Not a supported RPOSB2 backup or file damaged."
            ])
        }
        let salt=Data(file[magic.count..<(magic.count+saltLength)])
        let nonce=Data(file[(magic.count+saltLength)..<headerLength])
        let header=Data(file.prefix(headerLength))
        let body=Data(file.dropFirst(headerLength))
        let ciphertext=Data(body.dropLast(tagLength))
        let tag=Data(body.suffix(tagLength))
        do {
            let box=try AES.GCM.SealedBox(nonce:AES.GCM.Nonce(data:nonce),
                                          ciphertext:ciphertext,tag:tag)
            let decoded=try AES.GCM.open(box,using:key(passphrase:passphrase,salt:salt),
                                         authenticating:header)
            guard decoded.count <= maxPlaintext else { throw CocoaError(.fileReadTooLarge) }
            return decoded
        } catch {
            throw NSError(domain:"ReminderOS",code:56,userInfo:[
                NSLocalizedDescriptionKey:"Incorrect passphrase or damaged portable backup."
            ])
        }
    }
}
