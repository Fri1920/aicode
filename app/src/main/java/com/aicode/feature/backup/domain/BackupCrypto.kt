package com.aicode.feature.backup.domain

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的对称加密：PBKDF2WithHmacSHA256 派生密钥 + AES/GCM/NoPadding 加密。
 *
 * 口令不落盘、不记忆；盐与 IV 随每次加密随机生成并写入文件头。GCM 自带完整性校验，
 * 口令错误或文件被篡改时解密抛 [javax.crypto.AEADBadTagException]。
 */
object BackupCrypto {
    private const val ITERATIONS = 210_000
    private const val KEY_LEN_BITS = 256
    private const val GCM_TAG_BITS = 128

    const val SALT_LEN = 16
    const val IV_LEN = 12

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_LEN).also { random.nextBytes(it) }
    fun newIv(): ByteArray = ByteArray(IV_LEN).also { random.nextBytes(it) }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LEN_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encryptWithHeader(plain: ByteArray, password: CharArray): ByteArray {
        val salt = newSalt()
        val iv = newIv()
        val ciphertext = encrypt(plain, password, salt, iv)
        return ByteArray(SALT_LEN + IV_LEN + ciphertext.size).also { output ->
            System.arraycopy(salt, 0, output, 0, SALT_LEN)
            System.arraycopy(iv, 0, output, SALT_LEN, IV_LEN)
            System.arraycopy(ciphertext, 0, output, SALT_LEN + IV_LEN, ciphertext.size)
        }
    }

    fun decryptWithHeader(data: ByteArray, password: CharArray): ByteArray {
        require(data.size >= SALT_LEN + IV_LEN) { "Invalid encrypted AiCode backup file" }
        val salt = data.copyOfRange(0, SALT_LEN)
        val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val ciphertext = data.copyOfRange(SALT_LEN + IV_LEN, data.size)
        return decrypt(ciphertext, password, salt, iv)
    }

    fun encrypt(plain: ByteArray, password: CharArray, salt: ByteArray, iv: ByteArray): ByteArray {
        require(salt.size == SALT_LEN) { "Invalid salt length" }
        require(iv.size == IV_LEN) { "Invalid IV length" }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(plain)
    }

    fun decrypt(ciphertext: ByteArray, password: CharArray, salt: ByteArray, iv: ByteArray): ByteArray {
        require(salt.size == SALT_LEN) { "Invalid salt length" }
        require(iv.size == IV_LEN) { "Invalid IV length" }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
