package com.example.epassport.server.service

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerId
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.CertPathValidator
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate

/**
 * サーバー側用 CSCA 信頼ストア。
 */
class CscaTrustStore {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
    }

    private val certificateConverter = JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)

    /**
     * 信頼できるマスターリスト署名者証明書の SHA-256 フィンガープリント（大文字16進数、コロンなし）。
     * 空の場合はフィンガープリント検証をスキップする。
     */
    var trustedMasterListSignerFingerprints: Set<String> = emptySet()

    fun loadMasterList(masterListBytes: ByteArray) {
        try {
            val cmsSignedData = CMSSignedData(masterListBytes)
            val certificates = cmsSignedData.certificates
                ?.getMatches(null)
                ?.map { certificateConverter.getCertificate(it as X509CertificateHolder) }
                ?: emptyList()

            val signer = cmsSignedData.signerInfos.signers.firstOrNull()
                ?: throw IllegalArgumentException("Master list contains no signer information")

            val signerCert = findSignerCertificate(signer, certificates)
                ?: throw IllegalArgumentException("Signer certificate not found in master list")

            if (!verifyMasterListSignature(cmsSignedData, signer, signerCert)) {
                throw IllegalArgumentException("Master list signature verification failed")
            }

            for (cert in certificates) {
                addCertificate(cert)
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to load CSCA master list", e)
        }
    }

    fun addCertificate(certificate: X509Certificate) {
        keyStore.setCertificateEntry(certificate.subjectX500Principal.name, certificate)
    }

    fun verifySodSignature(sodBytes: ByteArray): Boolean {
        return try {
            val cmsSignedData = CMSSignedData(sodBytes)
            val signer = cmsSignedData.signerInfos.signers.firstOrNull() ?: return false

            // SODに添付された証明書群から、署名者の証明書（DS証明書）を取得する
            val certificatesStore = cmsSignedData.certificates
            val signerCertHolder = certificatesStore?.getMatches(null)
                ?.map { it as X509CertificateHolder }
                ?.firstOrNull { signer.sid.match(it) }
            
            val dsCert = if (signerCertHolder != null) {
                certificateConverter.getCertificate(signerCertHolder)
            } else {
                // バンドルされていない場合は信頼ストアから直接検索（フォールバック）
                findSignerCertificate(signer, getCertificates())
            } ?: return false

            // 1. 有効期限検証
            dsCert.checkValidity()

            // 2. Key Usage 検証 (digitalSignature = index 0)
            if (dsCert.keyUsage != null && !dsCert.keyUsage[0]) {
                return false
            }

            // 3. 証明書チェーン（DS -> CSCA）検証
            val cscaCerts = getCertificates()
            if (cscaCerts.isEmpty()) return false

            // DS証明書自体が信頼ストアに直接含まれているか（テスト用・自己署名ケースなど）
            val isDirectlyTrusted = cscaCerts.any { trusted ->
                trusted.subjectX500Principal == dsCert.subjectX500Principal &&
                        trusted.publicKey == dsCert.publicKey
            }

            if (!isDirectlyTrusted) {
                val selector = X509CertSelector().apply {
                    certificate = dsCert
                }
                val pkixParams = PKIXBuilderParameters(keyStore, selector).apply {
                    isRevocationEnabled = false // オフライン検証のためデフォルトはオフ
                }

                val certFactory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME)
                val certPath = certFactory.generateCertPath(listOf(dsCert))
                
                val validator = CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)
                validator.validate(certPath, pkixParams)
            }

            // 4. 数学的署名検証（DS証明書の公開鍵を用いる）
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(dsCert)

            signer.verify(verifier)
        } catch (e: Exception) {
            false
        }
    }

    fun getCertificates(): List<X509Certificate> {
        return keyStore.aliases().toList().mapNotNull { alias ->
            keyStore.getCertificate(alias) as? X509Certificate
        }
    }

    fun isEmpty(): Boolean = keyStore.size() == 0

    private fun findSignerCertificate(
        signer: SignerInformation,
        certificates: List<X509Certificate>
    ): X509Certificate? {
        val signerId = signer.sid
        certificates.forEach { cert ->
            try {
                if (signerId.match(cert)) return cert
            } catch (ignored: Exception) {
            }
        }
        return certificates.find { cert ->
            matchByIssuerAndSerial(signerId, cert) || matchBySubjectKeyIdentifier(signerId, cert)
        }
    }

    private fun matchByIssuerAndSerial(signerId: SignerId, cert: X509Certificate): Boolean {
        return try {
            val issuer = signerId.issuer
            val serial = signerId.serialNumber
            if (issuer == null || serial == null) return false
            issuer == org.bouncycastle.asn1.x500.X500Name.getInstance(cert.issuerX500Principal.encoded)
                    && serial == cert.serialNumber
        } catch (e: Exception) {
            false
        }
    }

    private fun matchBySubjectKeyIdentifier(signerId: SignerId, cert: X509Certificate): Boolean {
        return try {
            val signerSki = signerId.subjectKeyIdentifier ?: return false
            val ski = cert.getExtensionValue(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier.id)
                ?: return false
            val certSki = org.bouncycastle.asn1.ASN1OctetString.getInstance(
                org.bouncycastle.asn1.ASN1Primitive.fromByteArray(ski)
            ).octets
            signerSki.contentEquals(certSki)
        } catch (e: Exception) {
            false
        }
    }

    private fun verifyMasterListSignature(
        cmsSignedData: CMSSignedData,
        signer: SignerInformation,
        signerCert: X509Certificate
    ): Boolean {
        return try {
            // 1. 数学的署名検証
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(signerCert)
            val isSigValid = signer.verify(verifier)
            if (!isSigValid) return false

            // 2. 署名者の指紋検証（ピン留めリストがある場合のみ）
            if (trustedMasterListSignerFingerprints.isNotEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(signerCert.encoded)
                val fingerprint = hash.joinToString("") { "%02X".format(it) }
                if (!trustedMasterListSignerFingerprints.contains(fingerprint)) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
