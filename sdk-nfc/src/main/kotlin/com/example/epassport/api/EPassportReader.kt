package com.example.epassport.api

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.epassport.data.auth.BacAuthenticator
import com.example.epassport.data.auth.CscaTrustStore
import com.example.epassport.data.nfc.IsoDepTransceiver
import com.example.epassport.data.reader.IcaoDataGroupReader
import com.example.epassport.data.security.RuntimeSecurityChecker
import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.usecase.ReadPassportUseCase
import com.example.epassport.usecase.ReadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * @param context Android の Context（RASP チェックに使用）
     * @param tag Android の NFC Framework から取得した Tag オブジェクト
     * @param mrzData OCR 等で取得した MRZ (Machine Readable Zone) 情報
     * @param challenge サーバーが発行した検証用のワンタイム乱数（null の場合はSDKが自動生成します）
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

        readInternal(tag, mrzData, challenge, trustStore, onProgress)
    }

    /**
     * MRZ 情報を用いて NFC タグからパスポートデータを読み取る（RASP チェックなし）。
     *
     * **注意**: 本メソッドは実行環境の安全性を検証しない。セキュリティを重視する場合は
     * [read] (Context 付き) を使用すること。
     *
     * @param tag Android の NFC Framework から取得した Tag オブジェクト
     * @param mrzData OCR 等で取得した MRZ (Machine Readable Zone) 情報
     * @param challenge サーバーが発行した検証用のワンタイム乱数（null の場合はSDKが自動生成します）
     * @param onProgress 進行状況を受け取るオプションのコールバック
     * @return 読み取り結果 (Success または Error)
     */
    suspend fun read(
        tag: Tag,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult = withContext(Dispatchers.IO) {
        readInternal(tag, mrzData, challenge, trustStore, onProgress)
    }

    private suspend fun readInternal(
        tag: Tag,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult {
        val isoDep = IsoDep.get(tag) ?: return ReadResult.Error(
            EPassportException("Tag does not support IsoDep technology")
        )

        val transceiver = IsoDepTransceiver(isoDep)
        // タイムアウトを長めに設定 (BAC, 画像などの大きなデータの読み込みに対応するため)
        transceiver.timeout = 10000

        val authenticator = BacAuthenticator()
        val reader = IcaoDataGroupReader()
        val useCase = ReadPassportUseCase(authenticator, reader)

        return try {
            isoDep.connect()
            val passportData = useCase.execute(
                transceiver = transceiver,
                mrzData = mrzData,
                challenge = challenge,
                trustStore = trustStore,
                onProgress = { progress -> onProgress?.invoke(progress) }
            )
            ReadResult.Success(passportData)
        } catch (e: EPassportException) {
            ReadResult.Error(e)
        } catch (e: Exception) {
            // EPassportException 以外の予期しない例外（IOException等）もラップして返す
            ReadResult.Error(EPassportException("Unexpected error: ${e.message}", e))
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
