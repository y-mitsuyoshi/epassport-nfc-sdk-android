package com.example.epassport.data.auth

import com.example.epassport.domain.exception.InvalidDataException
import com.example.epassport.domain.model.ActiveAuthenticationData
import com.example.epassport.domain.model.AuthenticationStepResult
import com.example.epassport.domain.model.PassportVerificationResult
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

/**
 * パスポートの Passive Authentication（PA）と Active Authentication（AA）を
 * 正しい順序で統合実行する。
 *
 * 検証フロー:
 * 1. SOD の CSCA 署名検証
 * 2. SOD 内の DG ハッシュと実際に読み出した DG データのハッシュ照合
 * 3. DG15 が SOD に含まれていることの確認（改ざん検出）
 * 4. AA 署名検証（DG15 公開鍵を使用）
 */
class PassportVerifier {

    private val bcProvider = BouncyCastleProvider().also {
        if (Security.getProvider(it.name) == null) Security.addProvider(it)
    }

    /**
     * Passive Authentication のみ実行する。
     *
     * @param sodBytes EF.SOD の生バイト列
     * @param dataGroups 実際に読み出した DG 番号からバイト列へのマップ（DG1, DG2, DG15 など）
     * @param trustStore 信頼する CSCA 証明書を含む [CscaTrustStore]
     * @return PA ステップ結果
     */
    fun verifyPassiveAuthentication(
        sodBytes: ByteArray,
        dataGroups: Map<Int, ByteArray>,
        trustStore: CscaTrustStore
    ): AuthenticationStepResult {
        if (trustStore.isEmpty()) {
            return AuthenticationStepResult.failure("PA", "CSCA trust store is empty")
        }

        return try {
            val signatureValid = SodParser.verifySodSignature(sodBytes, trustStore)
            if (!signatureValid) {
                return AuthenticationStepResult.failure("PA", "SOD signature verification failed")
            }

            val hashesValid = SodParser.verifyHashes(sodBytes, dataGroups)
            if (!hashesValid) {
                return AuthenticationStepResult.failure("PA", "DG hash mismatch")
            }

            AuthenticationStepResult.success("PA", "SOD signature and DG hashes verified")
        } catch (e: Exception) {
            AuthenticationStepResult.failure("PA", e.message ?: "Unknown PA error")
        }
    }

    /**
     * Active Authentication のみ実行する。
     *
     * DG15 の信頼性は、呼び出し側が事前に PA で確認していることを前提とする。
     *
     * @param aaData Active Authentication データ
     * @return AA ステップ結果
     */
    fun verifyActiveAuthentication(aaData: ActiveAuthenticationData): AuthenticationStepResult {
        return try {
            val valid = AAVerifier.verify(aaData)
            if (valid) {
                AuthenticationStepResult.success("AA", "Active Authentication signature verified")
            } else {
                AuthenticationStepResult.failure("AA", "Active Authentication signature invalid")
            }
        } catch (e: Exception) {
            AuthenticationStepResult.failure("AA", e.message ?: "Unknown AA error")
        }
    }

    /**
     * PA と AA を統合して実行する。
     *
     * AA データが提供されていれば、PA 成功後に AA を実行する。
     * DG15 の信頼性は、SOD 内の DG15 ハッシュと読み出した DG15 データの一致で担保する。
     *
     * @param sodBytes EF.SOD の生バイト列
     * @param dataGroups 実際に読み出した DG 番号からバイト列へのマップ
     * @param aaData Active Authentication データ（null の場合は AA をスキップ）
     * @param trustStore 信頼する CSCA 証明書を含む [CscaTrustStore]
     * @return 統合検証結果
     */
    fun verify(
        sodBytes: ByteArray,
        dataGroups: Map<Int, ByteArray>,
        aaData: ActiveAuthenticationData?,
        trustStore: CscaTrustStore
    ): PassportVerificationResult {
        val paResult = verifyPassiveAuthentication(sodBytes, dataGroups, trustStore)
        if (!paResult.success) {
            return PassportVerificationResult.failure(paResult, null, paResult.detail)
        }

        // Verify that DG15 (if AA is requested) is covered by PA and matches the AA data.
        if (aaData != null) {
            val dg15Bytes = dataGroups[15]
                ?: return PassportVerificationResult.failure(
                    paResult,
                    null,
                    "AA requested but DG15 not provided for verification"
                )
            if (!aaData.publicKeyInfo.contentEquals(dg15Bytes)) {
                return PassportVerificationResult.failure(
                    paResult,
                    null,
                    "AA public key info does not match read DG15 (possible tampering)"
                )
            }
            if (!isDg15CoveredBySod(sodBytes, dg15Bytes)) {
                return PassportVerificationResult.failure(
                    paResult,
                    null,
                    "DG15 hash does not match SOD (possible tampering)"
                )
            }
        }

        val aaResult = aaData?.let { verifyActiveAuthentication(it) }
        return PassportVerificationResult.success(paResult, aaResult)
    }

    /**
     * SOD に含まれる DG15 のハッシュと、実際に読み出した DG15 データのハッシュが一致するか確認する。
     */
    private fun isDg15CoveredBySod(sodBytes: ByteArray, dg15Bytes: ByteArray): Boolean {
        return try {
            val expectedHashes = SodParser.parseDataGroupHashes(sodBytes)
            val expected = expectedHashes[15] ?: return false
            val digestAlgorithmOid = SodParser.parseDigestAlgorithm(sodBytes)
            val algorithmName = SodParser.digestAlgorithmOidToName(digestAlgorithmOid)
            val actual = SodParser.computeHash(dg15Bytes, algorithmName)
            MessageDigest.isEqual(expected, actual)
        } catch (e: Exception) {
            false
        }
    }
}
