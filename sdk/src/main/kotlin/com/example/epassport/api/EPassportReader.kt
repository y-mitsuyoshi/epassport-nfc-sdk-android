package com.example.epassport.api

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.epassport.data.auth.BacAuthenticator
import com.example.epassport.data.nfc.IsoDepTransceiver
import com.example.epassport.data.reader.IcaoDataGroupReader
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
     * MRZ 情報を用いて NFC タグからパスポートデータ (DG1, DG2) を読み取る。
     *
     * @param tag Android の NFC Framework から取得した Tag オブジェクト
     * @param mrzData OCR 等で取得した MRZ (Machine Readable Zone) 情報
     * @param onProgress 進行状況を受け取るオプションのコールバック
     * @return 読み取り結果 (Success または Error)
     */
    suspend fun read(
        tag: Tag,
        mrzData: MrzData,
        onProgress: ((ReadProgress) -> Unit)? = null
    ): ReadResult = withContext(Dispatchers.IO) {
        
        val isoDep = IsoDep.get(tag) ?: return@withContext ReadResult.Error(
            EPassportException("Tag does not support IsoDep technology")
        )

        val transceiver = IsoDepTransceiver(isoDep)
        // タイムアウトを長めに設定 (BAC, 画像などの大きなデータの読み込みに対応するため)
        transceiver.timeout = 10000 

        val authenticator = BacAuthenticator()
        val reader = IcaoDataGroupReader()
        val useCase = ReadPassportUseCase(authenticator, reader)

        return@withContext try {
            val passportData = useCase.execute(
                transceiver = transceiver,
                mrzData = mrzData,
                onProgress = { progress -> onProgress?.invoke(progress) }
            )
            ReadResult.Success(passportData)
        } catch (e: EPassportException) {
            ReadResult.Error(e)
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
