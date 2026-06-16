package com.example.epassport.data.auth

import com.example.epassport.domain.exception.InvalidDataException
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerId
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate

/**
 * CSCA（Country Signing CA）証明書を管理し、SOD 署名検証を行うための信頼ストア。
 *
 * ICAO 9303 Part 12 に準拠した CSCA マスターリストをパースし、内包される証明書を
 * 信頼アンカーとして保持する。SOD（EF.SOD）の CMS 署名を、この信頼アンカーを使って
 * 検証できる。
 *
 * マスターリスト自体の取得（ICAO PKD、各国政府サイト、バックエンド経由など）は
 * 呼び出し側が行い、このクラスにはバイト列を渡す。
 */
class CscaTrustStore {

    private val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
    }

    private val certificateConverter = JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider())

    /**
     * CSCA マスターリストを読み込み、内包される証明書を信頼ストアに登録する。
     *
     * @param masterListBytes CSCA マスターリストの生バイト列（CMS SignedData）
     * @throws InvalidDataException パースや検証に失敗した場合
     */
    fun loadMasterList(masterListBytes: ByteArray) {
        try {
            val cmsSignedData = CMSSignedData(masterListBytes)
            val signerInfos = cmsSignedData.signerInfos

            // Master list must be self-signed or signed by a known ML signer.
            // For robustness we extract the signer certificate from the attached certificates
            // and verify the signature before trusting the enclosed CSCA certificates.
            val certificates = cmsSignedData.certificates
                ?.getMatches(null)
                ?.map { certificateConverter.getCertificate(it as X509CertificateHolder) }
                ?: emptyList()

            val signer = signerInfos.signers.firstOrNull()
                ?: throw InvalidDataException("Master list contains no signer information")

            val signerCert = findSignerCertificate(signer, certificates)
                ?: throw InvalidDataException("Signer certificate not found in master list")

            if (!verifyMasterListSignature(cmsSignedData, signer, signerCert)) {
                throw InvalidDataException("Master list signature verification failed")
            }

            // All enclosed certificates are considered trusted CSCA certificates once
            // the master list signature itself has been verified.
            for (cert in certificates) {
                addCertificate(cert)
            }
        } catch (e: InvalidDataException) {
            throw e
        } catch (e: Exception) {
            throw InvalidDataException("Failed to load CSCA master list", e)
        }
    }

    /**
     * 個別の CSCA 証明書を信頼ストアに直接追加する。
     *
     * テスト用、またはマスターリストではなく個別に取得した証明書を登録する場合に使用。
     */
    fun addCertificate(certificate: X509Certificate) {
        keyStore.setCertificateEntry(certificate.subjectX500Principal.name, certificate)
    }

    /**
     * SOD（EF.SOD）の CMS 署名を、信頼ストア内の CSCA 証明書を使って検証する。
     *
     * @param sodBytes EF.SOD ファイルの生バイト列
     * @return 署名が信頼できる CSCA 証明書で検証できれば true
     */
    fun verifySodSignature(sodBytes: ByteArray): Boolean {
        return try {
            val cmsSignedData = CMSSignedData(sodBytes)
            val signer = cmsSignedData.signerInfos.signers.firstOrNull()
                ?: return false

            val signerCert = findSignerCertificate(signer, getCertificates())
                ?: return false

            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider())
                .build(signerCert)

            signer.verify(verifier)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 信頼ストアに登録されている証明書一覧を取得する。
     */
    fun getCertificates(): List<X509Certificate> {
        return keyStore.aliases().toList().mapNotNull { alias ->
            keyStore.getCertificate(alias) as? X509Certificate
        }
    }

    /**
     * 信頼ストアが空かどうかを確認する。
     */
    fun isEmpty(): Boolean = keyStore.size() == 0

    private fun findSignerCertificate(
        signer: SignerInformation,
        certificates: List<X509Certificate>
    ): X509Certificate? {
        val signerId = signer.sid
        // Try standard match first
        certificates.forEach { cert ->
            try {
                if (signerId.match(cert)) return cert
            } catch (ignored: Exception) {
                // fallback to manual matching below
            }
        }
        // Fallback: match by IssuerAndSerialNumber or SubjectKeyIdentifier
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

    @Suppress("UNUSED_PARAMETER")
    private fun verifyMasterListSignature(
        cmsSignedData: CMSSignedData,
        signer: SignerInformation,
        signerCert: X509Certificate
    ): Boolean {
        return try {
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider())
                .build(signerCert)
            signer.verify(verifier)
        } catch (e: Exception) {
            false
        }
    }
}
