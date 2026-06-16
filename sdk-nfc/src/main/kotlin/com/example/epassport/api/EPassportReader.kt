package com.example.epassport.api

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.epassport.data.auth.CscaTrustStore
import com.example.epassport.data.auth.PaceThenBacAuthenticator
import com.example.epassport.data.nfc.IsoDepTransceiver
import com.example.epassport.data.reader.IcaoDataGroupReader
import com.example.epassport.data.security.PlayIntegrityChecker
import com.example.epassport.data.security.RuntimeSecurityChecker
import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.usecase.CachedPassportData
import com.example.epassport.usecase.ReadPassportUseCase
import com.example.epassport.usecase.ReadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * SDK 利用者向けの Facade インターフェース。
 * 内部の複雑な APDU 通信を隠蔽し、シンプルなメソッドを提供する。
 */
object EPassportReader {

    // 瞬断からのレジューム用セキュアメモリキャッシュ（有効期限5分）
    private val cacheMap = java.util.concurrent.ConcurrentHashMap<String, CachedPassportData>()

    private fun cleanExpiredCache() {
        val iterator = cacheMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired()) {
                iterator.remove()
            }
        }
    }

    /**
     * MRZ 情報を用いて NFC タグからパスポートデータ (DG1, DG2, および Active Authenticationデータ) を読み取る。
     *
     * 本メソッドは実行環境の RASP チェック（root/エミュレータ/デバッグ検出）を行い、
     * 安全でない環境では即座にエラーを返す。
     *
     * @param context Android の Context（RASP チェックに使用）
     * @param tag Android の NFC Framework から取得した Tag オブジェクト
     * @param mrzData OCR 等で取得した MRZ (Machine Readable Zone) 情報
     * @param challenge サーバーが発行した検証用のワンタイム乱数（null の場合はSDKが自動生成します）
     * @param trustStore アクティブ認証/パッシブ認証検証用の CSCA 信頼局ストア
     * @param allowDebug デバッグ実行環境を許容するかどうか（RASPチェックのデバッグ検出の迂回）
     * @param googleCloudProjectNumber Google Cloud プロジェクト番号（Google Play Integrity API 呼び出しに必要）
     * @param onProgress 進行状況を受け取るオプションのコールバック
     * @return 読み取り結果 (Success または Error)
     */
    suspend fun read(
        context: Context,
        tag: Tag,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        allowDebug: Boolean = false,
        googleCloudProjectNumber: Long? = null,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult = withContext(Dispatchers.IO) {
        val securityChecker = RuntimeSecurityChecker(context, allowDebug = allowDebug)
        if (!securityChecker.isDeviceSecure()) {
            return@withContext ReadResult.Error(
                EPassportException(
                    "Unsafe execution environment detected: ${securityChecker.detectThreats().joinToString(", ")}"
                )
            )
        }

        // Google Play Integrity API による真正性検証トークン取得
        var integrityToken: String? = null
        if (googleCloudProjectNumber != null && challenge != null) {
            try {
                integrityToken = PlayIntegrityChecker.requestToken(
                    context = context,
                    cloudProjectNumber = googleCloudProjectNumber,
                    challenge = challenge
                )
            } catch (e: Exception) {
                if (!allowDebug) {
                    return@withContext ReadResult.Error(
                        EPassportException("Security validation failed: Play Integrity API token acquisition failed", e)
                    )
                } else {
                    android.util.Log.w("EPassportReader", "Play Integrity acquisition failed (allowed in debug): ${e.message}")
                }
            }
        }

        readInternal(tag, mrzData, challenge, trustStore, integrityToken, onProgress)
    }

    /**
     * MRZ 情報を用いて NFC タグからパスポートデータを読み取る（RASP チェックなし）。
     *
     * **注意**: 本メソッドは実行環境の安全性を検証しない。セキュリティを重視する場合は
     * [read] (Context 付き) を使用すること。
     */
    suspend fun read(
        tag: Tag,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult = withContext(Dispatchers.IO) {
        readInternal(tag, mrzData, challenge, trustStore, null, onProgress)
    }

    private suspend fun readInternal(
        tag: Tag,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        playIntegrityToken: String? = null,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult {
        val isoDep = IsoDep.get(tag) ?: return ReadResult.Error(
            EPassportException("Tag does not support IsoDep technology")
        )

        val transceiver = IsoDepTransceiver(isoDep)
        // タイムアウトを長めに設定 (BAC, 画像などの大きなデータの読み込みに対応するため)
        transceiver.timeout = 10000

        // PACE 優先 & BAC 自動フォールバック
        val authenticator = PaceThenBacAuthenticator()
        val reader = IcaoDataGroupReader()
        val useCase = ReadPassportUseCase(authenticator, reader)

        // キャッシュ関連処理（PIIを避けるため、MRZのSHA-256ハッシュ値をキーとする）
        cleanExpiredCache()
        val cacheKey = try {
            val mrzInfo = mrzData.mrzInformation
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(mrzInfo.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }

        val cachedData = cacheKey?.let { cacheMap[it] }

        return try {
            isoDep.connect()
            val passportData = useCase.execute(
                transceiver = transceiver,
                mrzData = mrzData,
                challenge = challenge,
                trustStore = trustStore,
                cachedData = cachedData,
                onCacheUpdate = { updated ->
                    cacheKey?.let { cacheMap[it] = updated }
                },
                onProgress = { progress -> onProgress?.invoke(progress) }
            )

            // 読み取り完了時はキャッシュから削除してメモリを解放
            cacheKey?.let { cacheMap.remove(it) }

            // Play Integrity Token を結果に注入
            val finalData = if (playIntegrityToken != null) {
                passportData.copy(playIntegrityToken = playIntegrityToken)
            } else {
                passportData
            }

            ReadResult.Success(finalData)
        } catch (e: EPassportException) {
            ReadResult.Error(e)
        } catch (e: Exception) {
            // EPassportException 以外の予期しない例外（IOException等）もラップして返す
            ReadResult.Error(EPassportException("Unexpected error during passport reading: ${e.message}", e))
        } finally {
            try {
                if (isoDep.isConnected) {
                    isoDep.close()
                }
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }
}
