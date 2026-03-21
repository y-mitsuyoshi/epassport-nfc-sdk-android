package com.example.epassport.util

import org.bouncycastle.crypto.engines.DESEngine
import org.bouncycastle.crypto.engines.DESedeEngine
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.ISO7816d4Padding
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 暗号処理のユーティリティ。BouncyCastle を内部的に利用。
 */
object CryptoUtils {
    /** ICAO 9303 Part 11 で使用する 3DES-CBC 暗号化 */
    fun encrypt3DesCbc(key: ByteArray, data: ByteArray, iv: ByteArray = ByteArray(8)): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC")
        val secretKey = SecretKeySpec(key, "DESede")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(data)
    }

    /** ICAO 9303 Part 11 で使用する 3DES-CBC 復号 */
    fun decrypt3DesCbc(key: ByteArray, data: ByteArray, iv: ByteArray = ByteArray(8)): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC")
        val secretKey = SecretKeySpec(key, "DESede")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(data)
    }

    /** ISO9797-1 Algorithm 3 MAC ( Retail MAC ) の計算 */
    fun calculateMac(key: ByteArray, data: ByteArray): ByteArray {
        val engine = DESEngine()
        val mac = ISO9797Alg3Mac(engine, 64)
        val keyParam = KeyParameter(key)
        mac.init(keyParam)
        mac.update(data, 0, data.size)
        val result = ByteArray(8)
        mac.doFinal(result, 0)
        return result
    }

    /** ISO7816-4 パディング (8バイトブロック) を追加 */
    fun pad(data: ByteArray): ByteArray {
        val padLength = 8 - (data.size % 8)
        val padded = ByteArray(data.size + padLength)
        System.arraycopy(data, 0, padded, 0, data.size)
        padded[data.size] = 0x80.toByte()
        // 残りは 0x00 になる (ByteArray 初期化時の挙動)
        return padded
    }

    /** ISO7816-4 パディングを削除 */
    fun unpad(data: ByteArray): ByteArray {
        var i = data.size - 1
        while (i >= 0 && data[i] == 0x00.toByte()) {
            i--
        }
        if (i >= 0 && data[i] == 0x80.toByte()) {
            val result = ByteArray(i)
            System.arraycopy(data, 0, result, 0, i)
            return result
        }
        return data // paddingが見つからない場合はそのまま
    }
}
