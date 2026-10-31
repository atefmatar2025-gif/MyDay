package com.dayflow.core.backup

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable snapshot of every table. Rows are plain maps produced/consumed by the app's
 * explicit entity mappers; the snapshot itself is only used for export/backup files.
 */
data class BackupSnapshot(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAtEpochMillis: Long,
    val tables: Map<String, List<Map<String, Any?>>>,
)

object BackupCodec {
    const val FORMAT = "dayflow-backup"
    const val FORMAT_VERSION = 1L

    fun toJson(s: BackupSnapshot): String = Json.write(
        linkedMapOf(
            "format" to FORMAT,
            "formatVersion" to FORMAT_VERSION,
            "schemaVersion" to s.schemaVersion.toLong(),
            "appVersion" to s.appVersion,
            "createdAt" to s.createdAtEpochMillis,
            "tables" to s.tables,
        ),
    )

    @Suppress("UNCHECKED_CAST")
    fun fromJson(text: String): BackupSnapshot {
        val root = Json.parse(text) as? Map<String, Any?> ?: throw BackupFormatException("الملف ليس نسخة احتياطية صالحة")
        if (root["format"] != FORMAT) throw BackupFormatException("الملف ليس نسخة احتياطية من DAYFLOW")
        val fv = (root["formatVersion"] as? Long) ?: 0
        if (fv > FORMAT_VERSION) throw BackupFormatException("النسخة الاحتياطية صادرة من إصدار أحدث من التطبيق")
        val tables = (root["tables"] as? Map<String, Any?>)?.mapValues { (_, v) ->
            (v as? List<Any?>)?.map { it as Map<String, Any?> } ?: emptyList()
        } ?: emptyMap()
        return BackupSnapshot(
            schemaVersion = (root["schemaVersion"] as? Long)?.toInt() ?: 1,
            appVersion = root["appVersion"] as? String ?: "",
            createdAtEpochMillis = root["createdAt"] as? Long ?: 0,
            tables = tables,
        )
    }
}

class BackupFormatException(message: String) : Exception(message)
class WrongPasswordException : Exception("كلمة المرور غير صحيحة أو الملف تالف")

/**
 * Backup file container:
 *   magic "DFLW" | version(1) | flags(1: bit0 = encrypted) | [salt(16) | iv(12)] | payload
 * payload = gzip(JSON) - encrypted with AES-256-GCM when a password is given.
 * Key derivation: PBKDF2WithHmacSHA256, 120 000 iterations. The password is never stored.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('D'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), 'W'.code.toByte())
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    fun pack(json: String, password: CharArray?): ByteArray {
        val compressed = gzip(json.toByteArray(Charsets.UTF_8))
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION.toInt())
        if (password == null || password.isEmpty()) {
            out.write(0)
            out.write(compressed)
        } else {
            val random = SecureRandom()
            val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            out.write(1)
            out.write(salt)
            out.write(iv)
            out.write(cipher.doFinal(compressed))
        }
        return out.toByteArray()
    }

    fun isEncrypted(data: ByteArray): Boolean {
        checkHeader(data)
        return data[5].toInt() and 1 == 1
    }

    fun unpack(data: ByteArray, password: CharArray?): String {
        checkHeader(data)
        val encrypted = data[5].toInt() and 1 == 1
        val payload = if (!encrypted) {
            data.copyOfRange(6, data.size)
        } else {
            if (password == null || password.isEmpty()) throw WrongPasswordException()
            if (data.size < 6 + SALT_LEN + IV_LEN + 16) throw BackupFormatException("الملف تالف")
            val salt = data.copyOfRange(6, 6 + SALT_LEN)
            val iv = data.copyOfRange(6 + SALT_LEN, 6 + SALT_LEN + IV_LEN)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            try {
                cipher.doFinal(data, 6 + SALT_LEN + IV_LEN, data.size - (6 + SALT_LEN + IV_LEN))
            } catch (e: AEADBadTagException) {
                throw WrongPasswordException()
            }
        }
        return gunzip(payload).toString(Charsets.UTF_8)
    }

    private fun checkHeader(data: ByteArray) {
        if (data.size < 6 || !data.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupFormatException("الملف ليس نسخة احتياطية من DAYFLOW")
        }
        if (data[4] > VERSION) throw BackupFormatException("إصدار ملف النسخة الاحتياطية غير مدعوم")
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return bos.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray = try {
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    } catch (e: Exception) {
        throw BackupFormatException("محتوى النسخة الاحتياطية تالف")
    }
}
