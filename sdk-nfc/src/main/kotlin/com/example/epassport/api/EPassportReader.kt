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

    /**
     * MRZ 情報を用いて NFC タグからパスポートデータ (DG1, DG2, および Active Authenticationデータ) を読み取る。
     *
     * 本メソッドは実行環境の RASP チェック（root/エミュレータ/デバッグ検出）を行い、
     * 安全でない環境では即座にエラーを返す。
     *
     * Google Play Integrity API が有効な場合（[googleCloudProjectNumber] が設定されている場合）、
     * デバイスとアプリの真正性を証明する Integrity Token が取得され、[PassportData.playIntegrityToken]
     * に格納されます。このトークンは [PassportData.toServerTransferData] 経由でバックエンドに送信し、
     * Google サーバーでの検証を受ける必要があります。バックエンドでの検証なしでは、トークンの
     * 改ざんを検出できません。
     *
     * @param context Android の Context（RASP チェックに使用）
     * @param tag Android の NFC Framework から取得した Tag オブジェクト
     * @param mrzData OCR 等で取得した MRZ (Machine Readable Zone) 情報
     * @param challenge サーバーが発行した検証用のワンタイム乱数。
     *   Active Authentication のチャレンジとして使用されます。
     *   null の場合は SDK が自動生成します。
     *   Google Play Integrity が有効な場合、この値の SHA-256 ハッシュが nonce として利用されます。
     * @param trustStore アクティブ認証/パッシブ認証検証用の CSCA 信頼局ストア
     * @param allowDebug デバッグ実行環境を許容するかどうか（RASPチェックのデバッグ検出の迂回）
     * @param googleCloudProjectNumber Google Cloud プロジェクト番号（Google Play Integrity API 呼び出しに必要）。
     *   設定すると Integrity Token が取得され結果に含まれます。バックエンドでの検証必須。
     * @param cachedData 瞬断からの復帰（レジューム）用の一時キャッシュデータ。呼び出し元が保持し、再接続時に渡します。
     * @param onCacheUpdate キャッシュデータが更新された（新しいデータグループの読み取りに成功した）際に呼ばれるコールバック。
     *   呼び出し元はここで渡されたキャッシュオブジェクトを保持し、再接続時に [cachedData] 引数として渡します。
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
        cachedData: CachedPassportData? = null,
        onCacheUpdate: ((CachedPassportData) -> Unit)? = null,
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
        if (googleCloudProjectNumber != null) {
            // challenge が null の場合は SDK が自動生成する
            val resolvedChallenge = challenge ?: ByteArray(32).apply {
                java.security.SecureRandom().nextBytes(this)
            }
            try {
                integrityToken = PlayIntegrityChecker.requestToken(
                    context = context,
                    cloudProjectNumber = googleCloudProjectNumber,
                    challenge = resolvedChallenge
                )
            } catch (e: Exception) {
                // 商用eKYCのコンバージョン率を損なわないため、取得失敗は致命的エラーにせず警告ログのみとする。
                // 真正性の最終判断はサーバー側で行う（トークンがない場合はサーバー側で追加の審査を行う等）。
                android.util.Log.w("EPassportReader", "Play Integrity token acquisition failed: ${e.message}", e)
            }
        }

        readInternal(tag, mrzData, challenge, trustStore, integrityToken, cachedData, onCacheUpdate, onProgress)
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
        cachedData: CachedPassportData? = null,
        onCacheUpdate: ((CachedPassportData) -> Unit)? = null,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult = withContext(Dispatchers.IO) {
        readInternal(tag, mrzData, challenge, trustStore, null, cachedData, onCacheUpdate, onProgress)
    }

    private suspend fun readInternal(
        tag: Tag,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        playIntegrityToken: String? = null,
        cachedData: CachedPassportData? = null,
        onCacheUpdate: ((CachedPassportData) -> Unit)? = null,
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

        return try {
            isoDep.connect()
            val passportData = useCase.execute(
                transceiver = transceiver,
                mrzData = mrzData,
                challenge = challenge,
                trustStore = trustStore,
                cachedData = cachedData,
                onCacheUpdate = { updated ->
                    onCacheUpdate?.invoke(updated)
                },
                onProgress = { progress -> onProgress?.invoke(progress) }
            )

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
