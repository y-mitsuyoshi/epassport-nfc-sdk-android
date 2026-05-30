package com.example.epassport.util

import java.io.Closeable

/**
 * メモリ上に残したくない機密データ（生の画像バイトや鍵など）を保持し、
 * 使い終わった際に安全にゼロクリアするためのラッパー。
 */
class SecureByteArray(var data: ByteArray) : Closeable {
    override fun close() {
        data.fill(0)
    }

    /** データの長さを返す。ゼロクリア後は元のサイズを返すことに注意 */
    val size: Int get() = data.size

    /** 内部の配列を直接返す。呼び出し元はコピーや保持に注意すること。 */
    fun unwrap(): ByteArray = data
}
