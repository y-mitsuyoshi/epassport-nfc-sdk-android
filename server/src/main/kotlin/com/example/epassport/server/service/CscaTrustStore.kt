package com.example.epassport.server.service

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerId
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * サーバー側用 CSCA 信頼ストア。
 *
 * クライアント SDK の [com.example.epassport.data.auth.CscaTrustStore] と同等の処理を行う。
 */
class CscaTrustStore {

    private val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
    }

    private val certificateConverter = JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider())

    fun loadMasterList(masterListBytes: ByteArray) {
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
    }

    fun addCertificate(certificate: X509Certificate) {
        keyStore.setCertificateEntry(certificate.subjectX500Principal.name, certificate)
    }

    fun verifySodSignature(sodBytes: ByteArray): Boolean {
        return try {
            val cmsSignedData = CMSSignedData(sodBytes)
            val signer = cmsSignedData.signerInfos.signers.firstOrNull() ?: return false
            val signerCert = findSignerCertificate(signer, getCertificates()) ?: return false
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider())
                .build(signerCert)
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
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider())
                .build(signerCert)
            signer.verify(verifier)
        } catch (e: Exception) {
            false
        }
    }
}
