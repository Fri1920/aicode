package com.aicode.feature.backup.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun encryptWithHeader_writesSaltIvAndCiphertextReadableByDecryptWithHeader() {
        val plain = "AiCode encrypted backup payload".toByteArray(Charsets.UTF_8)
        val password = "correct horse battery staple".toCharArray()

        val encrypted = BackupCrypto.encryptWithHeader(plain, password)

        assertTrue(encrypted.size > BackupCrypto.SALT_LEN + BackupCrypto.IV_LEN)
        assertArrayEquals(plain, BackupCrypto.decryptWithHeader(encrypted, password))
    }

    @Test
    fun encryptWithHeader_layoutMatchesImporterExpectation() {
        val plain = "snapshot.tar.gz bytes".toByteArray(Charsets.UTF_8)
        val password = "backup-password".toCharArray()

        val encrypted = BackupCrypto.encryptWithHeader(plain, password)
        val salt = encrypted.copyOfRange(0, BackupCrypto.SALT_LEN)
        val iv = encrypted.copyOfRange(BackupCrypto.SALT_LEN, BackupCrypto.SALT_LEN + BackupCrypto.IV_LEN)
        val ciphertext = encrypted.copyOfRange(BackupCrypto.SALT_LEN + BackupCrypto.IV_LEN, encrypted.size)

        assertArrayEquals(plain, BackupCrypto.decrypt(ciphertext, password, salt, iv))
    }

    @Test
    fun decryptWithHeader_rejectsDataTooShortForHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decryptWithHeader(ByteArray(BackupCrypto.SALT_LEN + BackupCrypto.IV_LEN - 1), "pw".toCharArray())
        }
    }
}