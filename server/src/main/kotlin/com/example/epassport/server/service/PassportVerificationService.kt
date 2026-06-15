package com.example.epassport.server.service

import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * サーバー側で Passive Authentication / Active Authentication を実行するサービス。
 */
@Service
class PassportVerificationService {

    data class VerificationResult(
        val successful: Boolean,
        val paSuccess: Boolean,
        val aaSuccess: Boolean?,
        val failureReason: String?
    )

    data class VerificationRequest(
        val sodBase64: String,
        val dataGroups: Map<Int, String>, // DG number -> Base64 encoded bytes
        val aaPublicKeyBase64: String?,
        val aaChallengeBase64: String?,
        val aaSignatureBase64: String?,
        val cscaMasterListBase64: String? // Optional: if provided, SOD signature is verified
    )

    fun verify(request: VerificationRequest): VerificationResult {
        val sodBytes = java.util.Base64.getDecoder().decode(request.sodBase64)
        val dataGroups = request.dataGroups.mapValues { (_, value) ->
            java.util.Base64.getDecoder().decode(value)
        }

        // Passive Authentication: verify SOD signature if trust store is available.
        val paSuccess = if (request.cscaMasterListBase64 != null) {
            val trustStore = CscaTrustStore()
            trustStore.loadMasterList(java.util.Base64.getDecoder().decode(request.cscaMasterListBase64))
            val signatureValid = trustStore.verifySodSignature(sodBytes)
            if (!signatureValid) {
                return VerificationResult(false, false, null, "SOD signature verification failed")
            }
            SodParser.verifyHashes(sodBytes, dataGroups)
        } else {
            SodParser.verifyHashes(sodBytes, dataGroups)
        }

        if (!paSuccess) {
            return VerificationResult(false, false, null, "DG hash mismatch")
        }

        // Active Authentication: optional.
        val aaSuccess = if (request.aaPublicKeyBase64 != null && request.aaChallengeBase64 != null && request.aaSignatureBase64 != null) {
            val dg15Bytes = dataGroups[15]
                ?: return VerificationResult(false, true, null, "AA requested but DG15 not provided")
            val aaPublicKey = java.util.Base64.getDecoder().decode(request.aaPublicKeyBase64)
            if (!MessageDigest.isEqual(dg15Bytes, aaPublicKey)) {
                return VerificationResult(false, true, null, "AA public key does not match DG15")
            }
            val aaData = AAVerifier.ActiveAuthenticationData(
                publicKeyInfo = aaPublicKey,
                challenge = java.util.Base64.getDecoder().decode(request.aaChallengeBase64),
                signature = java.util.Base64.getDecoder().decode(request.aaSignatureBase64)
            )
            AAVerifier.verify(aaData)
        } else null

        if (aaSuccess == false) {
            return VerificationResult(false, true, false, "Active Authentication signature invalid")
        }

        return VerificationResult(true, true, aaSuccess, null)
    }
}
